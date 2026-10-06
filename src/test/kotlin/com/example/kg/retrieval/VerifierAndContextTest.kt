package com.example.kg.retrieval

import com.example.kg.domain.model.AnswerDraft
import com.example.kg.domain.model.Channel
import com.example.kg.domain.model.Citation
import com.example.kg.domain.model.FusedContext
import com.example.kg.domain.model.GraphFact
import com.example.kg.domain.model.GraphFacts
import com.example.kg.retrieval.context.ContextBuilder
import com.example.kg.retrieval.verify.AnswerVerifier
import com.example.kg.support.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerifierAndContextTest {
    private val verifier = AnswerVerifier()
    private val ctx = FusedContext(Fixtures.errorFacts, emptyList(), emptyList(), emptyList())

    private fun draft(answer: String, vararg ids: String) =
        AnswerDraft(answer, ids.map { Citation(it, "GRAPH") })

    @Test
    fun `answer using only facts passes`() {
        val v = verifier.verify(draft("PAY-4031 안내: 마이페이지 > 결제수단 관리 > 카드 추가", "err:PAY-4031"), ctx, Channel.CUSTOMER)
        assertTrue(v.passed, v.violations.toString())
    }

    @Test
    fun `unknown error code and unverified path are flagged`() {
        val v = verifier.verify(draft("PAY-7777 오류. 설정 > 보안 > 해제", "err:PAY-4031"), ctx, Channel.CUSTOMER)
        val kinds = v.violations.map { it.kind }
        assertTrue("HALLUCINATED_ERROR_CODE" in kinds)
        assertTrue("UNVERIFIED_UI_PATH" in kinds)
    }

    @Test
    fun `citation outside context is flagged`() {
        val v = verifier.verify(draft("안내", "err:UNKNOWN"), ctx, Channel.CUSTOMER)
        assertTrue(v.violations.any { it.kind == "UNKNOWN_CITATION" })
    }

    @Test
    fun `internal solution is blocked for customer channel`() {
        val internal = FusedContext(
            GraphFacts(Fixtures.errorFacts.facts + GraphFact("sol:internal-reset", "Solution", mapOf("audience" to "INTERNAL"))),
            emptyList(), emptyList(), emptyList(),
        )
        val v = verifier.verify(draft("내부 조치", "sol:internal-reset"), internal, Channel.CUSTOMER)
        assertTrue(v.violations.any { it.kind == "AUDIENCE_LEAK" })
        assertTrue(verifier.verify(draft("내부 조치", "sol:internal-reset"), internal, Channel.AGENT).passed)
    }

    @Test
    fun `context builder adds app update note without llm`() {
        val prompt = ContextBuilder().userPrompt(
            com.example.kg.retrieval.analyze.QueryAnalyzer(com.example.kg.retrieval.analyze.PiiMasker()).analyze("PAY-4031 떠요"),
            ctx, "CUSTOMER", "5.1.0",
        )
        assertTrue(prompt.contains("min_app_version"), prompt)
        assertTrue(prompt.contains("""<fact id="err:PAY-4031" type="ErrorCode">"""))
    }

    @Test
    fun `version comparison is numeric per segment`() {
        assertEquals(-1, ContextBuilder.compareVersions("5.1.0", "5.2.0"))
        assertEquals(1, ContextBuilder.compareVersions("5.10.0", "5.9.9"))
        assertEquals(0, ContextBuilder.compareVersions("5.2", "5.2.0"))
    }

    @Test
    fun `facts with hidden governance props are not rendered`() {
        val prompt = ContextBuilder().userPrompt(
            com.example.kg.retrieval.analyze.QueryAnalyzer(com.example.kg.retrieval.analyze.PiiMasker()).analyze("PAY-4031"),
            ctx, "CUSTOMER", null,
        )
        assertFalse(prompt.contains("updated_at=2000"))
    }
}
