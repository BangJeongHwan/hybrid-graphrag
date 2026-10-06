package com.example.kg.retrieval.verify

import com.example.kg.domain.model.AnswerDraft
import com.example.kg.domain.model.Channel
import com.example.kg.domain.model.FusedContext
import com.example.kg.domain.model.Verdict
import com.example.kg.domain.model.Violation
import org.springframework.stereotype.Component

/**
 * 결정적 사후 검증. LLM 출력이 근거 밖의 사실을 말했는지 코드로 확인한다.
 * 1) 인용 ID 존재 2) 오류 코드 존재 3) 'A > B > C' 경로 존재 4) 고객 채널 내부 조치 노출
 */
@Component
class AnswerVerifier {
    private val errorCodeRegex = Regex("""\b[A-Z]{2,5}-\d{3,5}\b""")
    private val pathLineRegex = Regex("""[^\n]*>[^\n]*""")

    fun verify(draft: AnswerDraft, ctx: FusedContext, channel: Channel): Verdict {
        val violations = mutableListOf<Violation>()
        val factIds = ctx.facts.facts.map { it.id }.toSet()
        val chunkIds = ctx.docs.map { it.chunkId }.toSet()

        draft.citations.filter { it.id !in factIds && it.id !in chunkIds }
            .forEach { violations += Violation("UNKNOWN_CITATION", it.id) }

        val knownCodes = ctx.facts.errorCodes()
        errorCodeRegex.findAll(draft.answer).map { it.value }.filterNot { it in knownCodes }
            .forEach { violations += Violation("HALLUCINATED_ERROR_CODE", it) }

        val knownPaths = ctx.facts.ofType("UiPath").mapNotNull { (it.props["steps"] as? List<*>)?.map { s -> s.toString() } }
        val docText = ctx.docs.joinToString("\n") { it.content }
        pathLineRegex.findAll(draft.answer).map { m -> extractPath(m.value) }
            .filter { it.size >= 2 }
            .filterNot { path -> knownPaths.any { it == path } || docText.contains(path.joinToString(" > ")) }
            .forEach { violations += Violation("UNVERIFIED_UI_PATH", it.joinToString(" > ")) }

        if (channel == Channel.CUSTOMER) {
            val internalIds = ctx.facts.facts.filter { it.props["audience"] == "INTERNAL" }.map { it.id }.toSet()
            draft.citations.filter { it.id in internalIds }
                .forEach { violations += Violation("AUDIENCE_LEAK", it.id) }
        }
        return Verdict(violations)
    }

    /**
     * 'PAY-4031 안내: 마이페이지 > 결제수단 관리 > 카드 추가' 에서 경로 부분만 꺼낸다.
     * 첫 단계 앞의 문장은 마지막 ':' 뒤만 쓴다. (문장 전체를 정확히 파싱하지는 않는 근사치다.)
     */
    private fun extractPath(line: String): List<String> {
        val segments = line.split('>').map { it.trim() }.toMutableList()
        segments[0] = segments[0].substringAfterLast(':').trim()
        return segments.filter { it.isNotEmpty() }
    }
}
