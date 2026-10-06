package com.example.kg.retrieval

import com.example.kg.domain.model.Intent
import com.example.kg.retrieval.analyze.PiiMasker
import com.example.kg.retrieval.analyze.QueryAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyzerTest {
    private val analyzer = QueryAnalyzer(PiiMasker())

    @Test
    fun `error code question is classified and code extracted`() {
        val q = analyzer.analyze("카드 등록하는데 pay-4031 떠요")
        assertEquals(Intent.ERROR_RESOLUTION, q.intent)
        assertEquals(listOf("PAY-4031"), q.errorCodes)
        assertEquals(listOf("svc:payment"), q.serviceHints)
    }

    @Test
    fun `pii is masked before anything else sees it`() {
        val q = analyzer.analyze("010-1234-5678 로 연락주세요, 카드 1234-5678-9012-3456 안돼요")
        assertFalse(q.masked.contains("1234-5678"))
        assertTrue(q.masked.contains("[PHONE]"))
        assertTrue(q.masked.contains("[CARD]"))
        assertEquals(Intent.ISSUE_RESOLUTION, q.intent)
    }

    @Test
    fun `unrelated question is other`() {
        assertEquals(Intent.OTHER, analyzer.analyze("날씨 어때요").intent)
    }
}
