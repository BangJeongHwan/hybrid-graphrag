package com.example.kg.infra.rerank

import com.example.kg.domain.model.DocChunk
import com.example.kg.domain.port.RerankPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * 재정렬기가 아직 없을 때 쓰는 기본 구현. 질의 토큰이 문서에 얼마나 겹치는지로 점수를 준다.
 * 실제 cross-encoder가 준비되면 TeiRerankAdapter로 바꾼다.
 */
@Component
@ConditionalOnProperty("kg.rerank.provider", havingValue = "lexical", matchIfMissing = true)
class LexicalRerankAdapter : RerankPort {
    override fun rerank(query: String, docs: List<DocChunk>): List<DocChunk> {
        val terms = query.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }.toSet()
        if (terms.isEmpty()) return docs.map { it.copy(rerankScore = 0.0) }.sortedByDescending { it.score }
        return docs.map { d ->
            val text = d.content.lowercase()
            val hit = terms.count { text.contains(it) }
            d.copy(rerankScore = hit.toDouble() / terms.size)
        }.sortedWith(compareByDescending<DocChunk> { it.rerankScore }.thenByDescending { it.score })
    }
}

/** TEI rerank 서버 호출. POST {base}/rerank 응답은 [{index, score}] 이다. */
@Component
@ConditionalOnProperty("kg.rerank.provider", havingValue = "tei")
class TeiRerankAdapter(
    @Value("\${kg.rerank.tei-url}") baseUrl: String,
) : RerankPort {
    private val client = RestClient.builder().baseUrl(baseUrl).build()

    data class Scored(val index: Int, val score: Double)

    override fun rerank(query: String, docs: List<DocChunk>): List<DocChunk> {
        if (docs.isEmpty()) return docs
        val scores = client.post().uri("/rerank")
            .body(mapOf("query" to query, "texts" to docs.map { it.content }))
            .retrieve()
            .body(Array<Scored>::class.java)
            .orEmpty()
        return scores.map { docs[it.index].copy(rerankScore = it.score) }.sortedByDescending { it.rerankScore }
    }
}
