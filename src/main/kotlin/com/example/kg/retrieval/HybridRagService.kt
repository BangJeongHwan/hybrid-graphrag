package com.example.kg.retrieval

import com.example.kg.domain.model.AnalyzedQuery
import com.example.kg.domain.model.BranchResult
import com.example.kg.domain.model.FusedContext
import com.example.kg.domain.model.GraphFacts
import com.example.kg.domain.model.InquiryRequest
import com.example.kg.domain.model.InquiryResponse
import com.example.kg.domain.model.LinkedEntity
import com.example.kg.domain.model.ResponseMode
import com.example.kg.domain.port.LearningItem
import com.example.kg.domain.port.ReviewQueuePort
import com.example.kg.retrieval.analyze.QueryAnalyzer
import com.example.kg.retrieval.context.ContextBuilder
import com.example.kg.retrieval.fallback.FallbackPolicy
import com.example.kg.retrieval.fusion.FusionRanker
import com.example.kg.retrieval.generate.AnswerGenerator
import com.example.kg.retrieval.graph.GraphRetriever
import com.example.kg.retrieval.link.EntityLinker
import com.example.kg.retrieval.vector.VectorRetriever
import com.example.kg.retrieval.verify.AnswerVerifier
import com.example.kg.retrieval.verify.ConfidenceScorer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

/**
 * 질의 처리 오케스트레이터. 설계서 7.3의 흐름을 따른다.
 * 분석 → (그래프 ∥ 벡터) → 융합 → 생성 → 검증(1회 재시도) → Fallback → 신뢰도 → 학습 큐
 */
@Service
class HybridRagService(
    private val analyzer: QueryAnalyzer,
    private val linker: EntityLinker,
    private val graphRetriever: GraphRetriever,
    private val vectorRetriever: VectorRetriever,
    private val fusion: FusionRanker,
    private val contextBuilder: ContextBuilder,
    private val generator: AnswerGenerator,
    private val verifier: AnswerVerifier,
    private val fallback: FallbackPolicy,
    private val scorer: ConfidenceScorer,
    private val learningQueue: ReviewQueuePort,
    private val props: RetrievalProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private data class GraphBranch(val linked: List<LinkedEntity>, val facts: GraphFacts)

    suspend fun answer(req: InquiryRequest): InquiryResponse {
        val traceId = UUID.randomUUID().toString()
        val q = analyzer.analyze(req.question)
        val audiences = req.channel.allowedAudiences()

        // ① 병렬 검색: 한쪽이 실패하거나 timeout이 나도 다른 쪽은 살린다
        val (graph, vector) = coroutineScope {
            val g = async { runBranch("graph", props.graphTimeout) { graphBranch(q, audiences) } }
            val v = async { runBranch("vector", props.vectorTimeout) { withContext(Dispatchers.IO) { vectorRetriever.search(q, audiences) } } }
            g.await() to v.await()
        }
        val linked = graph.valueOrNull()?.linked.orEmpty()

        // ② 융합
        val ctx = fusion.fuse(q, graph.map { it.facts }, vector)

        // ③ 근거가 없으면 LLM을 호출하지 않는다 (환각 원천 차단)
        if (ctx.isEmpty()) {
            if (linker.isAmbiguous(linked)) {
                return respond(traceId, fallback.clarify(linked.map { it.id }), 0.0, ctx, req)
            }
            learningQueue.enqueue(LearningItem(traceId, req.question, "NO_EVIDENCE"))
            return respond(traceId, fallback.noEvidence(), 0.0, ctx, req)
        }

        // ④ 생성 → 검증 → (1회 재시도) → Fallback
        val system = contextBuilder.systemPrompt()
        val userPrompt = contextBuilder.userPrompt(q, ctx, req.channel.name, req.appVersion)
        val generated = withContext(Dispatchers.IO) { generator.generate(system, userPrompt) }
        if (generated == null) {
            learningQueue.enqueue(LearningItem(traceId, req.question, "LLM_FAILURE"))
            return respond(traceId, fallback.deterministic(ctx), 0.0, ctx, req)
        }

        var draft = generated
        val firstVerdict = verifier.verify(draft, ctx, req.channel)
        if (!firstVerdict.passed) {
            val retryPrompt = userPrompt + "\n<violations>\n" +
                firstVerdict.violations.joinToString("\n") { "- ${it.kind}: ${it.detail}" } + "\n</violations>"
            val retry = withContext(Dispatchers.IO) { generator.generate(system, retryPrompt) }
            if (retry == null || !verifier.verify(retry, ctx, req.channel).passed) {
                learningQueue.enqueue(LearningItem(traceId, req.question, "VERIFY_FAIL"))
                return respond(traceId, fallback.deterministic(ctx), 0.0, ctx, req)
            }
            draft = retry
        }

        // ⑤ 신뢰도와 학습 큐
        val confidence = scorer.score(linked, ctx, draft)
        when {
            ctx.conflicts.isNotEmpty() -> learningQueue.enqueue(LearningItem(traceId, req.question, "CONFLICT"))
            confidence < props.learningThreshold -> learningQueue.enqueue(LearningItem(traceId, req.question, "LOW_CONFIDENCE"))
        }
        log.debug("trace={} confidence={} degraded={}", traceId, confidence, ctx.degraded)
        return respond(traceId, FallbackPolicy.Rendered(ResponseMode.LLM, draft), confidence, ctx, req)
    }

    private suspend fun graphBranch(q: AnalyzedQuery, audiences: List<String>): GraphBranch =
        withContext(Dispatchers.IO) {
            val linked = linker.link(q)
            GraphBranch(linked, graphRetriever.retrieve(q, linked, audiences))
        }

    /** 한쪽 분기의 실패·timeout을 Degraded로 바꿔 나머지 분기가 계속 동작하게 한다. */
    private suspend fun <T> runBranch(name: String, timeout: Duration, block: suspend () -> T): BranchResult<T> =
        try {
            withTimeoutOrNull(timeout.toMillis()) { block() }
                ?.let { BranchResult.Ok(it) }
                ?: BranchResult.Degraded("timeout").also { log.warn("{} 분기 timeout", name) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("{} 분기 실패: {}", name, e.message, e)
            BranchResult.Degraded(e.javaClass.simpleName)
        }

    private fun respond(
        traceId: String,
        rendered: FallbackPolicy.Rendered,
        confidence: Double,
        ctx: FusedContext,
        req: InquiryRequest,
    ) = InquiryResponse(
        answer = rendered.draft.answer,
        citations = rendered.draft.citations,
        confidence = confidence,
        needsEscalation = rendered.draft.needsEscalation || (ctx.facts.ofType("Team").isNotEmpty() && confidence < props.learningThreshold),
        mode = rendered.mode,
        traceId = traceId,
        degraded = ctx.degraded,
    )
}
