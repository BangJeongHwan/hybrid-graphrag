package com.example.kg.infra.pgvector

import com.example.kg.domain.model.DocChunk
import com.example.kg.domain.port.SearchQuery
import com.example.kg.domain.port.VectorSearchPort
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Aurora PostgreSQL(pgvector) 하이브리드 검색. Dense(HNSW) + Lexical(GIN) 결과를 RRF로 합친다.
 * 필터 때문에 결과가 모자라는 문제는 iterative scan으로 완화한다(pgvector 0.8+).
 */
@Component
class PgVectorSearchAdapter(private val jdbc: JdbcClient) : VectorSearchPort {

    @Transactional(readOnly = true)
    override fun hybridSearch(query: SearchQuery): List<DocChunk> {
        jdbc.sql("SET LOCAL hnsw.ef_search = 100").update()
        jdbc.sql("SET LOCAL hnsw.iterative_scan = relaxed_order").update()

        val tsquery = query.lexicalTerms.filter { it.isNotBlank() }
            .distinct()
            .joinToString(" | ") { "'" + it.replace("'", "''") + "'" }
            .ifEmpty { null }

        return jdbc.sql(SQL)
            .param("qvec", vectorLiteral(query.queryVector))
            .param("audiences", query.audiences.toTypedArray())
            .param("services", query.services.takeIf { it.isNotEmpty() }?.toTypedArray())
            .param("tsquery", tsquery)
            .param("limit", query.limit)
            .query { rs, _ ->
                DocChunk(
                    chunkId = rs.getString("chunk_id"),
                    docId = rs.getString("doc_id"),
                    title = rs.getString("title"),
                    sectionPath = (rs.getArray("section_path")?.array as? Array<*>)?.map { it.toString() }.orEmpty(),
                    serviceIds = (rs.getArray("service_ids")?.array as? Array<*>)?.map { it.toString() }.orEmpty(),
                    entityIds = (rs.getArray("entity_ids")?.array as? Array<*>)?.map { it.toString() }.orEmpty(),
                    audience = rs.getString("audience"),
                    updatedAt = rs.getTimestamp("updated_at").toInstant().toEpochMilli(),
                    content = rs.getString("content"),
                    score = rs.getDouble("rrf"),
                )
            }
            .list()
    }

    private fun vectorLiteral(v: FloatArray) = v.joinToString(prefix = "[", postfix = "]", separator = ",")

    companion object {
        /** tsquery가 null이면 lexical 분기를 건너뛴다. */
        private const val SQL = """
            WITH dense AS (
              SELECT chunk_id, row_number() OVER (ORDER BY embedding <=> CAST(:qvec AS vector)) AS r
              FROM rag.doc_chunk
              WHERE status = 'ACTIVE' AND audience = ANY(:audiences)
                AND (CAST(:services AS text[]) IS NULL OR service_ids && CAST(:services AS text[]))
              ORDER BY embedding <=> CAST(:qvec AS vector)
              LIMIT 50
            ),
            lexical AS (
              SELECT chunk_id, row_number() OVER (ORDER BY ts_rank_cd(lexical, q) DESC) AS r
              FROM rag.doc_chunk, to_tsquery('simple', COALESCE(CAST(:tsquery AS text), '')) q
              WHERE CAST(:tsquery AS text) IS NOT NULL AND lexical @@ q
                AND status = 'ACTIVE' AND audience = ANY(:audiences)
                AND (CAST(:services AS text[]) IS NULL OR service_ids && CAST(:services AS text[]))
              ORDER BY ts_rank_cd(lexical, q) DESC
              LIMIT 50
            ),
            fused AS (
              SELECT chunk_id, sum(1.0 / (60 + r)) AS rrf
              FROM (SELECT * FROM dense UNION ALL SELECT * FROM lexical) u
              GROUP BY chunk_id
            )
            SELECT c.chunk_id, c.doc_id, c.title, c.section_path, c.service_ids, c.entity_ids,
                   c.audience, c.updated_at, c.content, f.rrf
            FROM fused f JOIN rag.doc_chunk c USING (chunk_id)
            ORDER BY f.rrf DESC
            LIMIT :limit
        """

        /** 인덱싱: 임베딩과 형태소 토큰을 함께 저장한다. */
        const val UPSERT = """
            INSERT INTO rag.doc_chunk
              (chunk_id, doc_id, doc_type, title, section_path, service_ids, entity_ids, issue_id, audience,
               status, lang, content, content_hash, kg_version, updated_at, embedding, lexical)
            VALUES
              (:chunkId, :docId, :docType, :title, :sectionPath, :serviceIds, :entityIds, :issueId, :audience,
               'ACTIVE', 'ko', :content, :contentHash, :kgVersion, to_timestamp(:updatedAt / 1000.0),
               CAST(:embedding AS vector), to_tsvector('simple', :tokens))
            ON CONFLICT (chunk_id) DO UPDATE SET
              content = EXCLUDED.content, content_hash = EXCLUDED.content_hash, embedding = EXCLUDED.embedding,
              lexical = EXCLUDED.lexical, entity_ids = EXCLUDED.entity_ids, updated_at = EXCLUDED.updated_at,
              kg_version = EXCLUDED.kg_version, status = 'ACTIVE'
        """
    }
}
