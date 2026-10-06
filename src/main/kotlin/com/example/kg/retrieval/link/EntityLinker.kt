package com.example.kg.retrieval.link

import com.example.kg.domain.model.AnalyzedQuery
import com.example.kg.domain.model.LinkMethod
import com.example.kg.domain.model.LinkedEntity
import com.example.kg.domain.ontology.NodeLabel
import com.example.kg.domain.port.EmbeddingPort
import com.example.kg.domain.port.GraphReadPort
import com.example.kg.retrieval.RetrievalProperties
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Component

/**
 * 질의 → 그래프 시작 노드. 정확 매칭(오류 코드)을 먼저 보고, 나머지는 벡터 kNN으로 찾는다.
 * 정확 매칭 결과가 있으면 벡터 후보는 보조로만 쓴다.
 */
@Component
class EntityLinker(
    private val graph: GraphReadPort,
    private val embedding: EmbeddingPort,
    private val props: RetrievalProperties,
) {
    suspend fun link(q: AnalyzedQuery): List<LinkedEntity> = coroutineScope {
        val exact = async { if (q.errorCodes.isEmpty()) emptyList() else graph.findErrorCodes(q.errorCodes) }
        val vector = async {
            val vec = embedding.embed(q.rewritten)
            listOf(NodeLabel.ISSUE, NodeLabel.FEATURE).flatMap { graph.vectorSearch(it, vec, props.linkTopK) }
        }
        val exactLinks = exact.await()
        val vectorLinks = vector.await()
            .filter { it.score >= props.linkMinSimilarity }
            .map { it.copy(method = LinkMethod.VECTOR) }

        (exactLinks + vectorLinks)
            .distinctBy { it.id }
            .sortedByDescending { it.score }
    }

    /** 점수 차가 작은 서로 다른 서비스 후보가 있으면 되묻기를 해야 한다. */
    fun isAmbiguous(links: List<LinkedEntity>): Boolean {
        val top = links.take(2)
        return top.size == 2 && top[0].method == LinkMethod.VECTOR &&
            top[0].label != top[1].label && top[0].score - top[1].score < props.ambiguityMargin
    }
}
