package com.example.kg.retrieval.analyze

import org.springframework.stereotype.Component

/** 질의와 학습 큐에 들어가기 전에 개인정보 패턴을 마스킹한다. */
@Component
class PiiMasker {
    private val rules: List<Pair<Regex, String>> = listOf(
        Regex("""\b\d{4}[- ]?\d{4}[- ]?\d{4}[- ]?\d{4}\b""") to "[CARD]",
        Regex("""\b01[016789][- ]?\d{3,4}[- ]?\d{4}\b""") to "[PHONE]",
        Regex("""[\w.+-]+@[\w-]+\.[\w.]+""") to "[EMAIL]",
        Regex("""\bORD-\d{6,}\b""") to "[ORDER]",
    )

    fun mask(text: String): String = rules.fold(text) { acc, (regex, token) -> regex.replace(acc, token) }
}
