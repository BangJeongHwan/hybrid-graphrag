package com.example.kg.retrieval.vector

import com.example.kg.domain.model.AnalyzedQuery
import com.example.kg.domain.model.DocChunk
import com.example.kg.domain.port.EmbeddingPort
import com.example.kg.domain.port.LexicalTokenizer
import com.example.kg.domain.port.SearchQuery
import com.example.kg.domain.port.VectorSearchPort
import com.example.kg.retrieval.RetrievalProperties
import org.springframework.stereotype.Component

/** 질의를 임베딩과 형태소 토큰으로 바꿔 하이브리드 검색을 요청한다. */
@Component
class VectorRetriever(
    private val vectorPort: VectorSearchPort,
    private val embedding: EmbeddingPort,
    private val tokenizer: LexicalTokenizer,
    private val props: RetrievalProperties,
) {
    fun search(q: AnalyzedQuery, audiences: List<String>): List<DocChunk> {
        val query = SearchQuery(
            text = q.rewritten,
            queryVector = embedding.embed(q.rewritten),
            lexicalTerms = tokenizer.tokenize(q.rewritten) + q.errorCodes.map { it.lowercase() },
            audiences = audiences,
            services = q.serviceHints,
            limit = props.vectorTopK,
        )
        return vectorPort.hybridSearch(query)
    }
}
