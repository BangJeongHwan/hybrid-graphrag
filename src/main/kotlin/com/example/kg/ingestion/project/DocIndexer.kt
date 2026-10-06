package com.example.kg.ingestion.project

import com.example.kg.domain.model.IndexedChunk
import com.example.kg.domain.port.DocumentIndexPort
import com.example.kg.domain.port.EmbeddingPort
import com.example.kg.domain.port.LexicalTokenizer
import org.springframework.stereotype.Component

data class DocumentInput(
    val docId: String,
    val docType: String,
    val title: String,
    val sectionPath: List<String>,
    val serviceIds: List<String>,
    val entityIds: List<String>,
    val audience: String,
    val content: String,
    val updatedAt: Long,
)

/**
 * 청크를 색인한다. 설계서 3.4의 contextual header를 임베딩 입력에만 붙이고,
 * 저장하는 본문은 원문 그대로 둔다.
 */
@Component
class DocIndexer(
    private val embedding: EmbeddingPort,
    private val tokenizer: LexicalTokenizer,
    private val store: DocumentIndexPort,
) {
    fun index(doc: DocumentInput, chunkIndex: Int = 0): String {
        val chunkId = "${doc.docId}#$chunkIndex"
        val header = "[${doc.title}${doc.sectionPath.joinToString("") { " > $it" }}]\n"
        store.upsert(
            IndexedChunk(
                chunkId = chunkId,
                docId = doc.docId,
                docType = doc.docType,
                title = doc.title,
                sectionPath = doc.sectionPath,
                serviceIds = doc.serviceIds,
                entityIds = doc.entityIds,
                issueId = doc.entityIds.firstOrNull { it.startsWith("issue:") },
                audience = doc.audience,
                content = doc.content,
                kgVersion = 0,
                updatedAt = doc.updatedAt,
                embedding = embedding.embed(header + doc.content),
                tokens = tokenizer.tokenize(header + doc.content),
            ),
        )
        return chunkId
    }
}
