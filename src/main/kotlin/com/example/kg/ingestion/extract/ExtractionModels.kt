package com.example.kg.ingestion.extract

/** LLM이 낸 추출 결과(JSON). 검증 전의 원시 모양이다. */
data class ExtractionResult(
    val nodes: List<ExtractedNode> = emptyList(),
    val edges: List<ExtractedEdge> = emptyList(),
)

data class ExtractedNode(
    val tempId: String = "",
    val refId: String? = null,
    val label: String = "",
    val properties: Map<String, Any?> = emptyMap(),
    val evidence: String = "",
    val confidence: Double = 0.0,
)

data class ExtractedEdge(
    val src: String = "",
    val rel: String = "",
    val dst: String = "",
    val properties: Map<String, Any?> = emptyMap(),
    val evidence: String = "",
    val confidence: Double = 0.0,
)
