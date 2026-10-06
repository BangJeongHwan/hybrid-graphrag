package com.example.kg.retrieval.generate

import com.example.kg.domain.model.AnswerDraft
import com.example.kg.domain.model.Citation
import com.example.kg.domain.port.LlmPort
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** LLM 호출과 JSON 파싱. 실패하면 null을 돌려 Fallback이 처리하게 한다. */
@Component
class AnswerGenerator(
    private val llm: LlmPort,
    private val mapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun generate(system: String, user: String): AnswerDraft? = try {
        val raw = llm.complete(system, user)
        mapper.readValue<LlmAnswer>(extractJson(raw)).toDraft()
    } catch (e: Exception) {
        log.warn("LLM 응답 생성/파싱 실패: {}", e.message)
        null
    }

    /** 모델이 ```json 펜스를 붙이거나 앞뒤에 설명을 붙여도 첫 JSON 객체만 꺼낸다. */
    private fun extractJson(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "JSON 객체를 찾지 못했다" }
        return raw.substring(start, end + 1)
    }
}

/** 프롬프트의 JSON 계약(snake_case)을 그대로 받는 DTO. 도메인 모델에 Jackson 어노테이션을 두지 않는다. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class LlmAnswer(
    val answer: String = "",
    val citations: List<LlmCitation> = emptyList(),
    @JsonProperty("used_solution_ids") val usedSolutionIds: List<String> = emptyList(),
    @JsonProperty("needs_escalation") val needsEscalation: Boolean = false,
    val confidence: Double = 0.0,
    @JsonProperty("missing_info") val missingInfo: List<String> = emptyList(),
) {
    fun toDraft() = AnswerDraft(
        answer = answer,
        citations = citations.map { Citation(it.id, it.type) },
        usedSolutionIds = usedSolutionIds,
        needsEscalation = needsEscalation,
        confidence = confidence,
        missingInfo = missingInfo,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class LlmCitation(val id: String = "", val type: String = "GRAPH")
