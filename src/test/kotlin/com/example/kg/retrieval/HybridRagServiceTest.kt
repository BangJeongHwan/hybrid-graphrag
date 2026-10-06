package com.example.kg.retrieval

import com.example.kg.domain.model.Channel
import com.example.kg.domain.model.InquiryRequest
import com.example.kg.domain.model.ResponseMode
import com.example.kg.infra.review.InMemoryReviewQueue
import com.example.kg.infra.rerank.LexicalRerankAdapter
import com.example.kg.infra.embed.FakeEmbeddingAdapter
import com.example.kg.infra.lexical.NoriTokenizer
import com.example.kg.retrieval.analyze.PiiMasker
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
import com.example.kg.support.FakeGraph
import com.example.kg.support.FakeVector
import com.example.kg.support.ScriptedLlm
import com.example.kg.support.chunk
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HybridRagServiceTest {

    private val goodAnswer = """{"answer":"카드사 앱에서 온라인 결제를 허용해 주세요. PAY-4031은 카드사 차단입니다.",
        "citations":[{"id":"err:PAY-4031","type":"GRAPH"},{"id":"doc:faq-payment#1","type":"DOC"}],
        "used_solution_ids":["sol:payment:enable-online-pay"],"needs_escalation":false,"confidence":0.9,"missing_info":[]}"""

    /** 그래프가 실패한 상황에서는 그래프 사실을 인용하면 안 된다(UNKNOWN_CITATION). 문서만 인용한다. */
    private val docOnlyAnswer = """{"answer":"카드 등록 오류 안내 문서를 참고해 주세요.",
        "citations":[{"id":"doc:faq-payment#1","type":"DOC"}],"used_solution_ids":[],"needs_escalation":false,"confidence":0.7,"missing_info":[]}"""

    private val hallucinated = """{"answer":"PAY-9999 오류입니다.","citations":[{"id":"err:PAY-4031","type":"GRAPH"}],
        "used_solution_ids":[],"needs_escalation":false,"confidence":0.9,"missing_info":[]}"""

    private fun service(
        llm: ScriptedLlm,
        graph: FakeGraph = FakeGraph(),
        vector: FakeVector = FakeVector(listOf(chunk())),
        queue: InMemoryReviewQueue = InMemoryReviewQueue(),
    ): HybridRagService {
        // 테스트에서는 사전 로딩(cold start)이 timeout에 걸리지 않도록 넉넉히 준다. 운영 기본값은 800ms.
        val props = RetrievalProperties(graphTimeout = Duration.ofSeconds(10), vectorTimeout = Duration.ofSeconds(10))
        val embedding = FakeEmbeddingAdapter(NoriTokenizer())
        val mapper = jacksonObjectMapper()
        return HybridRagService(
            analyzer = QueryAnalyzer(PiiMasker()),
            linker = EntityLinker(graph, embedding, props),
            graphRetriever = GraphRetriever(graph),
            vectorRetriever = VectorRetriever(vector, embedding, NoriTokenizer(), props),
            fusion = FusionRanker(LexicalRerankAdapter(), props),
            contextBuilder = ContextBuilder(),
            generator = AnswerGenerator(llm, mapper),
            verifier = AnswerVerifier(),
            fallback = FallbackPolicy(),
            scorer = ConfidenceScorer(),
            learningQueue = queue,
            props = props,
        )
    }

    private val question = InquiryRequest("카드 등록하는데 PAY-4031 떠요", Channel.CUSTOMER)

    @Test
    fun `grounded answer passes verification and cites graph facts`() = runBlocking {
        val res = service(ScriptedLlm(listOf(goodAnswer))).answer(question)
        assertEquals(ResponseMode.LLM, res.mode)
        assertTrue(res.citations.any { it.id == "err:PAY-4031" })
        assertTrue(res.confidence > 0.0)
        assertTrue(res.degraded.isEmpty())
    }

    @Test
    fun `hallucinated error code is rejected twice and falls back to deterministic render`() = runBlocking {
        val queue = InMemoryReviewQueue()
        val llm = ScriptedLlm(listOf(hallucinated))
        val res = service(llm, queue = queue).answer(question)
        assertEquals(ResponseMode.DETERMINISTIC, res.mode)
        assertFalse(res.answer.contains("PAY-9999"))
        assertTrue(res.answer.contains("PAY-4031"))
        assertEquals(2, llm.calls, "검증 실패 후 재생성 1회까지만 호출한다")
        assertTrue(queue.snapshot().any { it.reason == "VERIFY_FAIL" })
    }

    @Test
    fun `invalid llm json falls back to deterministic render`() = runBlocking {
        val res = service(ScriptedLlm(listOf("죄송합니다, JSON이 아닙니다"))).answer(question)
        assertEquals(ResponseMode.DETERMINISTIC, res.mode)
    }

    @Test
    fun `graph failure degrades but vector evidence still answers`() = runBlocking {
        val res = service(ScriptedLlm(listOf(docOnlyAnswer)), graph = FakeGraph(fail = true)).answer(question)
        assertTrue(res.degraded.any { it.startsWith("graph:") })
        assertEquals(ResponseMode.LLM, res.mode, "벡터 근거가 있으면 LLM 답변을 낸다")
    }

    @Test
    fun `no evidence never calls the llm`() = runBlocking {
        val llm = ScriptedLlm(listOf(goodAnswer))
        val queue = InMemoryReviewQueue()
        val res = service(llm, graph = FakeGraph(fail = true), vector = FakeVector(emptyList()), queue = queue)
            .answer(InquiryRequest("전혀 모르는 질문입니다"))
        assertEquals(ResponseMode.NO_EVIDENCE, res.mode)
        assertEquals(0, llm.calls)
        assertTrue(queue.snapshot().any { it.reason == "NO_EVIDENCE" })
    }

    @Test
    fun `document older than graph is reported as conflict and queued`() = runBlocking {
        val queue = InMemoryReviewQueue()
        val stale = chunk(updatedAt = 1000L) // 그래프 err:PAY-4031 updated_at=2000 보다 오래됨
        val res = service(ScriptedLlm(listOf(goodAnswer)), vector = FakeVector(listOf(stale)), queue = queue)
            .answer(question)
        assertEquals(ResponseMode.LLM, res.mode)
        assertTrue(queue.snapshot().any { it.reason == "CONFLICT" })
    }
}
