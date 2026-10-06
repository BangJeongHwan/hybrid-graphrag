package com.example.kg.domain.port

import com.example.kg.domain.model.DocChunk
import com.example.kg.domain.model.GraphFacts
import com.example.kg.domain.model.LinkedEntity
import com.example.kg.domain.ontology.NodeLabel

/** 그래프 읽기. 구현체는 반드시 읽기 전용 쿼리만 실행한다. */
interface GraphReadPort {
    fun findErrorCodes(codes: List<String>): List<LinkedEntity>
    fun vectorSearch(label: NodeLabel, vector: FloatArray, k: Int): List<LinkedEntity>
    fun errorResolution(codes: List<String>, audiences: List<String>): GraphFacts
    fun issueResolution(issueIds: List<String>, audiences: List<String>): GraphFacts
}

/** 문서 청크 검색(dense + lexical 하이브리드). */
interface VectorSearchPort {
    fun hybridSearch(query: SearchQuery): List<DocChunk>
}

data class SearchQuery(
    val text: String,
    val queryVector: FloatArray,
    val lexicalTerms: List<String>,
    val audiences: List<String>,
    val services: List<String>,
    val limit: Int,
)

interface EmbeddingPort {
    val dimension: Int
    fun embed(text: String): FloatArray
    fun embedAll(texts: List<String>): List<FloatArray> = texts.map { embed(it) }
}

/** Cross-encoder 재정렬. 질의와 문서를 함께 입력받아 관련도 점수를 준다. */
interface RerankPort {
    fun rerank(query: String, docs: List<DocChunk>): List<DocChunk>
}

interface LlmPort {
    /** 시스템 프롬프트(고정부)와 사용자 프롬프트(가변부)를 받아 모델 응답 텍스트를 돌려준다. */
    fun complete(system: String, user: String): String
}

interface LexicalTokenizer {
    fun tokenize(text: String): List<String>
}

/** Active Learning 큐. 저신뢰·미답변·충돌 질의를 쌓는다. */
interface ReviewQueuePort {
    fun enqueue(item: LearningItem)
}

data class LearningItem(
    val traceId: String,
    val question: String,
    val reason: String,
)

/** 그래프 쓰기(투영 전용). Cypher는 템플릿에서만 만든다. */
interface GraphWritePort {
    fun execute(cypher: String)
}

/** 문서 청크 색인(쓰기). */
interface DocumentIndexPort {
    fun upsert(chunk: com.example.kg.domain.model.IndexedChunk)
}
