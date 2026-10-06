package com.example.kg.domain.model

import com.example.kg.domain.ontology.NodeLabel

enum class Intent { ERROR_RESOLUTION, ISSUE_RESOLUTION, HOW_TO, FEATURE_EXPLAIN, OTHER }

enum class Channel { CUSTOMER, AGENT;
    /** 채널별로 노출할 수 있는 조치 대상(audience). */
    fun allowedAudiences(): List<String> = when (this) {
        CUSTOMER -> listOf("CUSTOMER")
        AGENT -> listOf("CUSTOMER", "AGENT")
    }
}

/** 질의 분석 결과. 이후 모든 단계는 `masked`만 사용한다. */
data class AnalyzedQuery(
    val original: String,
    val masked: String,
    val rewritten: String,
    val intent: Intent,
    val serviceHints: List<String>,
    val errorCodes: List<String>,
)

data class LinkedEntity(
    val id: String,
    val label: NodeLabel,
    val score: Double,
    val method: LinkMethod,
)

enum class LinkMethod { EXACT, VECTOR }

/** 그래프에서 읽어 온 사실 한 건. props는 화면/프롬프트에 쓰는 속성만 담는다. */
data class GraphFact(
    val id: String,
    val type: String,
    val props: Map<String, Any?> = emptyMap(),
)

/** 그래프 조회 결과. 사실의 집합이며 LLM에게는 Ground Truth로 전달된다. */
data class GraphFacts(val facts: List<GraphFact>) {
    fun isEmpty() = facts.isEmpty()
    fun nodeIds(): Set<String> = facts.mapTo(HashSet()) { it.id }
    fun ofType(type: String): List<GraphFact> = facts.filter { it.type == type }
    fun errorCodes(): Set<String> = ofType("ErrorCode").mapTo(HashSet()) { it.props["code"].toString() }

    companion object {
        val EMPTY = GraphFacts(emptyList())
    }
}

/** 벡터/키워드 검색으로 찾은 문서 청크. */
data class DocChunk(
    val chunkId: String,
    val docId: String,
    val title: String,
    val sectionPath: List<String>,
    val serviceIds: List<String>,
    val entityIds: List<String>,
    val audience: String,
    val updatedAt: Long,
    val content: String,
    val score: Double,
    val rerankScore: Double? = null,
)

/** 검색 분기 한쪽의 결과. 실패·timeout은 degraded로 표시해 Fallback 판단에 쓴다. */
sealed interface BranchResult<out T> {
    data class Ok<T>(val value: T) : BranchResult<T>
    data class Degraded(val reason: String) : BranchResult<Nothing>

    fun valueOrNull(): T? = (this as? Ok)?.value

    fun <R> map(f: (T) -> R): BranchResult<R> = when (this) {
        is Ok -> Ok(f(value))
        is Degraded -> this
    }
}

data class FusedContext(
    val facts: GraphFacts,
    val docs: List<DocChunk>,
    val conflicts: List<Conflict>,
    val degraded: List<String>,
) {
    fun isEmpty() = facts.isEmpty() && docs.isEmpty()
}

data class Conflict(val factId: String, val chunkId: String, val reason: String)

data class Citation(val id: String, val type: String)

/** LLM이 낸 초안. JSON으로 파싱된다. */
data class AnswerDraft(
    val answer: String,
    val citations: List<Citation>,
    val usedSolutionIds: List<String> = emptyList(),
    val needsEscalation: Boolean = false,
    val confidence: Double = 0.0,
    val missingInfo: List<String> = emptyList(),
)

data class Violation(val kind: String, val detail: String)

data class Verdict(val violations: List<Violation>) {
    val passed: Boolean get() = violations.isEmpty()
}

data class InquiryRequest(
    val question: String,
    val channel: Channel = Channel.CUSTOMER,
    val appVersion: String? = null,
)

data class InquiryResponse(
    val answer: String,
    val citations: List<Citation>,
    val confidence: Double,
    val needsEscalation: Boolean,
    val mode: ResponseMode,
    val traceId: String,
    val degraded: List<String>,
)

enum class ResponseMode { LLM, DETERMINISTIC, NO_EVIDENCE, CLARIFY }
