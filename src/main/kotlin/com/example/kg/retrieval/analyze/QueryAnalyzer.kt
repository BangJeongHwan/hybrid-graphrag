package com.example.kg.retrieval.analyze

import com.example.kg.domain.model.AnalyzedQuery
import com.example.kg.domain.model.Intent
import org.springframework.stereotype.Component

/**
 * 규칙 기반 질의 분석기.
 * 설계서는 의도/재작성에 Haiku 4.5 structured output을 권장했다. 이 샘플은 같은 출력 모양을
 * 규칙으로 먼저 만들고, LLM 보강은 `QueryRewriter` 자리에 꽂을 수 있게 분리했다.
 */
@Component
class QueryAnalyzer(
    private val pii: PiiMasker,
) {
    private val errorCodeRegex = Regex("""\b[A-Za-z]{2,5}-\d{3,5}\b""")
    private val serviceKeywords = mapOf(
        "결제" to "svc:payment",
        "카드" to "svc:payment",
        "주문" to "svc:order",
        "회원" to "svc:member",
    )
    private val errorWords = listOf("오류", "에러", "error", "떠요", "뜨", "실패", "코드")
    private val issueWords = listOf("안돼", "안 돼", "안됨", "안 됨", "문제", "안되", "실패")
    private val howToWords = listOf("어떻게", "방법", "하는법", "하는 법")

    fun analyze(question: String): AnalyzedQuery {
        val masked = pii.mask(question)
        val codes = errorCodeRegex.findAll(masked).map { it.value.uppercase() }.distinct().toList()
        val services = serviceKeywords.filterKeys { masked.contains(it) }.values.distinct()
        val intent = when {
            codes.isNotEmpty() -> Intent.ERROR_RESOLUTION
            issueWords.any { masked.contains(it) } -> Intent.ISSUE_RESOLUTION
            howToWords.any { masked.contains(it) } -> Intent.HOW_TO
            errorWords.any { masked.contains(it) } -> Intent.ISSUE_RESOLUTION
            else -> Intent.OTHER
        }
        return AnalyzedQuery(
            original = question,
            masked = masked,
            rewritten = masked.replace(Regex("\\s+"), " ").trim(),
            intent = intent,
            serviceHints = services,
            errorCodes = codes,
        )
    }
}
