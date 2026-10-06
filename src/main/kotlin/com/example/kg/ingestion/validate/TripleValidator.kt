package com.example.kg.ingestion.validate

import com.example.kg.domain.model.KgBatch
import com.example.kg.domain.model.KgEdge
import com.example.kg.domain.model.KgNode
import com.example.kg.domain.ontology.AllowedTriples
import com.example.kg.domain.ontology.NodeLabel
import com.example.kg.domain.ontology.RelType
import com.example.kg.ingestion.extract.ExtractedEdge
import com.example.kg.ingestion.extract.ExtractedNode
import com.example.kg.ingestion.extract.ExtractionResult
import org.springframework.stereotype.Component

data class ValidationOutcome(
    val accepted: KgBatch,
    val rejected: List<Rejection>,
)

data class Rejection(val item: String, val reason: String)

/**
 * LLM 추출 결과를 DRAFT 후보로 바꾼다. 규칙:
 * 1) 레이블·관계가 enum에 있는가 2) (src, rel, dst) 조합이 화이트리스트에 있는가
 * 3) evidence가 원문의 부분 문자열인가(공백 정규화) 4) confidence가 0~1 범위인가
 */
@Component
class TripleValidator {

    fun validate(result: ExtractionResult, sourceText: String, knownLabels: Map<String, NodeLabel> = emptyMap()): ValidationOutcome {
        val rejected = mutableListOf<Rejection>()
        val normalizedSource = normalize(sourceText)
        val nodes = mutableMapOf<String, NodeLabel>()
        val acceptedNodes = mutableListOf<KgNode>()

        result.nodes.forEach { n ->
            val label = parseLabel(n.label)
            val reason = when {
                label == null -> "알 수 없는 레이블 ${n.label}"
                !evidenceOk(n.evidence, normalizedSource) -> "evidence가 원문에 없음"
                !confidenceOk(n.confidence) -> "confidence 범위 오류"
                else -> null
            }
            if (reason != null || label == null) {
                rejected += Rejection("node:${n.tempId}", reason ?: "레이블 오류")
            } else {
                val id = n.refId ?: idFor(n, label)
                nodes[n.tempId] = label
                if (n.refId != null) knownLabels[n.refId]?.let { nodes[n.tempId] = it }
                acceptedNodes += KgNode(id, label, n.properties + draftGovernance(n))
            }
        }

        val acceptedEdges = mutableListOf<KgEdge>()
        result.edges.forEach { e ->
            val rel = parseRel(e.rel)
            val srcLabel = nodes[e.src] ?: knownLabels[e.src]
            val dstLabel = nodes[e.dst] ?: knownLabels[e.dst]
            val reason = when {
                rel == null -> "알 수 없는 관계 ${e.rel}"
                srcLabel == null || dstLabel == null -> "끝점 노드가 없음"
                !AllowedTriples.isAllowed(srcLabel, rel, dstLabel) -> "허용되지 않은 트리플 ${srcLabel}-[$rel]->${dstLabel}"
                !evidenceOk(e.evidence, normalizedSource) -> "evidence가 원문에 없음"
                !confidenceOk(e.confidence) -> "confidence 범위 오류"
                else -> null
            }
            if (reason != null) {
                rejected += Rejection("edge:${e.src}-${e.rel}->${e.dst}", reason)
            } else {
                acceptedEdges += KgEdge(
                    srcId = resolveId(e.src, result), srcLabel = srcLabel!!, rel = rel!!,
                    dstId = resolveId(e.dst, result), dstLabel = dstLabel!!,
                    props = e.properties + draftGovernance(e.evidence, e.confidence),
                )
            }
        }
        return ValidationOutcome(KgBatch(acceptedNodes, acceptedEdges), rejected)
    }

    private fun resolveId(ref: String, result: ExtractionResult): String =
        result.nodes.firstOrNull { it.tempId == ref }?.let { it.refId ?: idFor(it, parseLabel(it.label)!!) } ?: ref

    /** canonical id 규칙을 따른다. 같은 대상이 다시 나와도 같은 id가 되도록 slug를 쓴다. */
    private fun idFor(n: ExtractedNode, label: NodeLabel): String {
        val name = (n.properties["title"] ?: n.properties["name"] ?: n.properties["code"] ?: n.tempId).toString()
        return "${label.name.lowercase()}:${slug(name)}"
    }

    private fun slug(s: String) = s.trim().lowercase().replace(Regex("[^a-z0-9가-힣]+"), "-").trim('-')

    private fun draftGovernance(n: ExtractedNode) = draftGovernance(n.evidence, n.confidence)

    private fun draftGovernance(evidence: String, confidence: Double) = mapOf(
        "status" to "DRAFT", "confidence" to confidence, "source" to "LLM_EXTRACT",
        "source_ref" to evidence.take(200), "version" to 1, "updated_at" to System.currentTimeMillis(),
    )

    private fun parseLabel(s: String): NodeLabel? = NodeLabel.entries.firstOrNull { it.cypher == s }
    private fun parseRel(s: String): RelType? = RelType.entries.firstOrNull { it.cypher == s }

    private fun evidenceOk(evidence: String, normalizedSource: String) =
        evidence.isNotBlank() && normalizedSource.contains(normalize(evidence))

    private fun confidenceOk(c: Double) = c in 0.0..1.0

    private fun normalize(s: String) = s.replace(Regex("\\s+"), " ").trim()
}
