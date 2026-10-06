package com.example.kg.domain.model

/** 벡터 저장소에 쓰는 청크 한 건. 임베딩과 형태소 토큰을 함께 담는다. */
data class IndexedChunk(
    val chunkId: String,
    val docId: String,
    val docType: String,
    val title: String,
    val sectionPath: List<String>,
    val serviceIds: List<String>,
    val entityIds: List<String>,
    val issueId: String?,
    val audience: String,
    val content: String,
    val kgVersion: Long,
    val updatedAt: Long,
    val embedding: FloatArray,
    val tokens: List<String>,
)
