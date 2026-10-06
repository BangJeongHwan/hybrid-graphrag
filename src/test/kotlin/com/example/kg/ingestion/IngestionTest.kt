package com.example.kg.ingestion

import com.example.kg.domain.model.KgBatch
import com.example.kg.domain.ontology.CypherLiteral
import com.example.kg.domain.ontology.NodeLabel
import com.example.kg.domain.ontology.RelType
import com.example.kg.ingestion.extract.ExtractedEdge
import com.example.kg.ingestion.extract.ExtractedNode
import com.example.kg.ingestion.extract.ExtractionResult
import com.example.kg.ingestion.parse.ErrorEnumParser
import com.example.kg.ingestion.project.GraphProjector
import com.example.kg.ingestion.validate.TripleValidator
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.example.kg.domain.port.GraphWritePort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class IngestionTest {
    private val sample = javaClass.getResource("/samples/payment-errors.json")!!.readText()

    @Test
    fun `error enum parser builds approved error codes and feature-api chain`() {
        val batch = ErrorEnumParser(jacksonObjectMapper()).parse(sample)
        val errors = batch.nodes.filter { it.label == NodeLabel.ERROR_CODE }
        assertEquals(setOf("err:PAY-4031", "err:PAY-4032"), errors.map { it.id }.toSet())
        assertTrue(errors.all { it.props["status"] == "APPROVED" && it.props["source"] == "ENUM" })
        assertTrue(batch.edges.any { it.rel == RelType.RETURNS && it.dstId == "err:PAY-4031" })
        assertTrue(batch.edges.any { it.rel == RelType.EXPOSED_BY })
    }

    @Test
    fun `validator accepts allowed triple with grounded evidence`() {
        val source = "카드 등록 시 '승인 거절' 메시지가 표시되는 경우 카드사 차단을 확인하세요."
        val result = ExtractionResult(
            nodes = listOf(
                ExtractedNode("n1", null, "Issue", mapOf("title" to "카드 등록 실패"), "카드 등록 시 '승인 거절' 메시지가 표시되는 경우", 0.9),
                ExtractedNode("n2", null, "Cause", mapOf("title" to "카드사 차단"), "카드사 차단을 확인하세요", 0.8),
            ),
            edges = listOf(ExtractedEdge("n1", "HAS_CAUSE", "n2", emptyMap(), "카드사 차단을 확인하세요", 0.8)),
        )
        val out = TripleValidator().validate(result, source)
        assertTrue(out.rejected.isEmpty(), out.rejected.toString())
        assertEquals(1, out.accepted.edges.size)
        assertEquals("DRAFT", out.accepted.nodes.first().props["status"])
    }

    @Test
    fun `validator rejects disallowed triple and fabricated evidence`() {
        val source = "원문 한 줄입니다."
        val result = ExtractionResult(
            nodes = listOf(
                ExtractedNode("n1", null, "Service", mapOf("name" to "결제"), "원문 한 줄입니다.", 0.9),
                ExtractedNode("n2", null, "ErrorCode", mapOf("code" to "PAY-1"), "없는 문장", 0.9),
            ),
            edges = listOf(ExtractedEdge("n1", "RETURNS", "n2", emptyMap(), "원문 한 줄입니다.", 0.9)),
        )
        val out = TripleValidator().validate(result, source)
        val reasons = out.rejected.map { it.reason }
        assertTrue(reasons.any { it.contains("evidence") })
        assertTrue(out.accepted.edges.isEmpty(), "Service-RETURNS->ErrorCode 는 화이트리스트에 없다")
    }

    @Test
    fun `projector emits idempotent MERGE with escaped literals`() {
        val written = mutableListOf<String>()
        val projector = GraphProjector(object : GraphWritePort {
            override fun execute(cypher: String) { written += cypher }
        })
        projector.project(
            KgBatch(
                nodes = listOf(com.example.kg.domain.model.KgNode("issue:x", NodeLabel.ISSUE, mapOf("title" to "it's \"bad\""))),
                edges = emptyList(),
            ),
        )
        assertTrue(written.single().startsWith("MERGE (n:Issue {id: 'issue:x'})"))
        assertTrue(written.single().contains("'it\\'s \"bad\"'"))
        assertFalse(written.single().contains("DELETE"))
    }

    @Test
    fun `cypher literal renders lists and nulls`() {
        assertEquals("['a', 'b']", CypherLiteral.render(listOf("a", "b")))
        assertEquals("null", CypherLiteral.render(null))
        assertEquals("[0.5, 1.0]", CypherLiteral.render(floatArrayOf(0.5f, 1.0f)))
    }
}
