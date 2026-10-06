package com.example.kg.retrieval.fusion

import com.example.kg.domain.model.AnalyzedQuery
import com.example.kg.domain.model.BranchResult
import com.example.kg.domain.model.Conflict
import com.example.kg.domain.model.DocChunk
import com.example.kg.domain.model.FusedContext
import com.example.kg.domain.model.GraphFacts
import com.example.kg.domain.port.RerankPort
import com.example.kg.retrieval.RetrievalProperties
import org.springframework.stereotype.Component

/**
 * 그래프 결과와 문서 결과를 하나의 컨텍스트로 합친다.
 * 1) 그래프가 확정한 서비스 밖의 문서 제외 2) 그래프 노드와 연결된 청크 가산점
 * 3) reranker로 재정렬 4) 그래프보다 오래된 문서 충돌 표시
 */
@Component
class FusionRanker(
    private val reranker: RerankPort,
    private val props: RetrievalProperties,
) {
    fun fuse(
        q: AnalyzedQuery,
        graph: BranchResult<GraphFacts>,
        vector: BranchResult<List<DocChunk>>,
    ): FusedContext {
        val degraded = listOfNotNull(
            (graph as? BranchResult.Degraded)?.let { "graph:${it.reason}" },
            (vector as? BranchResult.Degraded)?.let { "vector:${it.reason}" },
        )
        val facts = graph.valueOrNull() ?: GraphFacts.EMPTY
        val chunks = vector.valueOrNull().orEmpty()

        val graphServiceIds = facts.ofType("Service").mapTo(HashSet()) { it.id }
        val scoped = if (graphServiceIds.isEmpty()) chunks
        else chunks.filter { c -> c.serviceIds.isEmpty() || c.serviceIds.any { it in graphServiceIds } }

        val nodeIds = facts.nodeIds()
        val boosted = scoped.map { c ->
            val overlap = c.entityIds.count { it in nodeIds }
            c.copy(score = c.score + props.graphBoost * minOf(overlap, 3))
        }

        val reranked = reranker.rerank(q.rewritten, boosted)
            .filter { (it.rerankScore ?: 0.0) >= props.minRerankScore }
            .take(props.maxDocs)

        return FusedContext(
            facts = facts,
            docs = reranked,
            conflicts = detectConflicts(facts, reranked),
            degraded = degraded,
        )
    }

    /** 문서가 그래프 노드 갱신 시각보다 오래됐으면 노후화 충돌로 본다(설계 P12). */
    private fun detectConflicts(facts: GraphFacts, docs: List<DocChunk>): List<Conflict> {
        val updatedAt = facts.facts.associate { it.id to (it.props["updated_at"] as? Long) }
        return docs.flatMap { doc ->
            doc.entityIds.mapNotNull { entityId ->
                val graphUpdated = updatedAt[entityId] ?: return@mapNotNull null
                if (doc.updatedAt < graphUpdated) Conflict(entityId, doc.chunkId, "DOC_OLDER_THAN_GRAPH") else null
            }
        }
    }
}
