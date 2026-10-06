package com.example.kg.retrieval.fallback

import com.example.kg.domain.model.AnswerDraft
import com.example.kg.domain.model.FusedContext
import com.example.kg.domain.model.GraphFact
import com.example.kg.domain.model.ResponseMode
import org.springframework.stereotype.Component

/** LLM 없이 그래프 사실만으로 고정 템플릿 답변을 만든다. 근거가 없으면 안내 문구만 낸다. */
@Component
class FallbackPolicy {

    data class Rendered(val mode: ResponseMode, val draft: AnswerDraft)

    fun noEvidence(): Rendered = Rendered(
        ResponseMode.NO_EVIDENCE,
        AnswerDraft(
            answer = "문의하신 내용을 확인할 수 있는 정보를 찾지 못했습니다. 확인 후 안내드리겠습니다.",
            citations = emptyList(),
            needsEscalation = true,
            confidence = 0.0,
        ),
    )

    fun clarify(options: List<String>): Rendered = Rendered(
        ResponseMode.CLARIFY,
        AnswerDraft(
            answer = "어떤 기능을 말씀하시는지 확인이 필요합니다: " + options.joinToString(", "),
            citations = emptyList(),
            confidence = 0.0,
        ),
    )

    /** 설계서 8.3의 DeterministicRenderer. 그래프 사실만 쓰므로 환각이 없다. */
    fun deterministic(ctx: FusedContext): Rendered {
        val lines = mutableListOf<String>()
        val citations = mutableListOf<com.example.kg.domain.model.Citation>()
        ctx.facts.ofType("ErrorCode").forEach { e ->
            lines += "[${e.props["code"]}] ${e.props["user_message"] ?: ""}".trim()
            citations += com.example.kg.domain.model.Citation(e.id, "GRAPH")
        }
        ctx.facts.ofType("Cause").forEach { c ->
            lines += "확인 방법: ${c.props["check_method"] ?: c.props["description"] ?: ""}"
            citations += com.example.kg.domain.model.Citation(c.id, "GRAPH")
        }
        ctx.facts.ofType("Solution").forEach { s ->
            lines += "조치 방법: ${s.props["title"] ?: ""}"
            lines += steps(s).mapIndexed { i, step -> " ${i + 1}. $step" }
            citations += com.example.kg.domain.model.Citation(s.id, "GRAPH")
        }
        ctx.facts.ofType("UiPath").forEach { u ->
            lines += "경로: " + steps(u).joinToString(" > ")
            citations += com.example.kg.domain.model.Citation(u.id, "GRAPH")
        }
        ctx.facts.ofType("Team").firstOrNull()?.let { t ->
            lines += "해결되지 않으면 ${t.props["name"]}으로 문의해 주세요."
            citations += com.example.kg.domain.model.Citation(t.id, "GRAPH")
        }
        if (lines.isEmpty()) return noEvidence()
        return Rendered(
            ResponseMode.DETERMINISTIC,
            AnswerDraft(
                answer = lines.joinToString("\n"),
                citations = citations,
                needsEscalation = ctx.facts.ofType("Team").isNotEmpty(),
                confidence = 0.5,
            ),
        )
    }

    private fun steps(f: GraphFact): List<String> = (f.props["steps"] ?: f.props["ui_steps"] ?: f.props["solution_steps"])
        .let { it as? List<*> }?.map { it.toString() }.orEmpty()
}
