package com.example.kg.domain.model

import com.example.kg.domain.ontology.NodeLabel
import com.example.kg.domain.ontology.RelType

/** 적재 단위. 파서(L0)와 LLM 추출(L2)이 모두 이 모양으로 만든다. */
data class KgNode(
    val id: String,
    val label: NodeLabel,
    val props: Map<String, Any?>,
)

data class KgEdge(
    val srcId: String,
    val srcLabel: NodeLabel,
    val rel: RelType,
    val dstId: String,
    val dstLabel: NodeLabel,
    val props: Map<String, Any?> = emptyMap(),
)

data class KgBatch(
    val nodes: List<KgNode>,
    val edges: List<KgEdge>,
) {
    operator fun plus(other: KgBatch) = KgBatch(nodes + other.nodes, edges + other.edges)
}
