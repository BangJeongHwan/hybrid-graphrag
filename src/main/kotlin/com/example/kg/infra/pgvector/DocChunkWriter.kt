package com.example.kg.infra.pgvector

import com.example.kg.domain.model.IndexedChunk
import com.example.kg.domain.port.DocumentIndexPort
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest

@Component
class DocChunkWriter(private val jdbc: JdbcClient) : DocumentIndexPort {

    @Transactional
    override fun upsert(chunk: IndexedChunk) {
        jdbc.sql(PgVectorSearchAdapter.UPSERT)
            .param("chunkId", chunk.chunkId)
            .param("docId", chunk.docId)
            .param("docType", chunk.docType)
            .param("title", chunk.title)
            .param("sectionPath", chunk.sectionPath.toTypedArray())
            .param("serviceIds", chunk.serviceIds.toTypedArray())
            .param("entityIds", chunk.entityIds.toTypedArray())
            .param("issueId", chunk.issueId)
            .param("audience", chunk.audience)
            .param("content", chunk.content)
            .param("contentHash", sha256(chunk.content))
            .param("kgVersion", chunk.kgVersion)
            .param("updatedAt", chunk.updatedAt)
            .param("embedding", chunk.embedding.joinToString(prefix = "[", postfix = "]", separator = ","))
            .param("tokens", chunk.tokens.joinToString(" "))
            .update()
    }

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
