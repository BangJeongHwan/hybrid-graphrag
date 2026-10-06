package com.example.kg.infra.llm

import com.example.kg.domain.port.LlmPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * API 키 없이 파이프라인을 돌리기 위한 결정적 모의 LLM.
 * 프롬프트의 <fact> 줄만 근거로 삼아 JSON을 만든다. 그래서 검증기를 통과하는 답변이 나온다.
 */
@Component
@ConditionalOnProperty("kg.llm.provider", havingValue = "fake", matchIfMissing = true)
class FakeLlmAdapter : LlmPort {
    private val factRegex = Regex("""<fact id="([^"]+)" type="([^"]+)">(.*?)</fact>""")
    private val docRegex = Regex("""<doc id="([^"]+)"[^>]*>\s*(.*?)\s*</doc>""", RegexOption.DOT_MATCHES_ALL)

    override fun complete(system: String, user: String): String {
        val facts = factRegex.findAll(user).toList()
        val docs = docRegex.findAll(user).toList()
        val factLines = facts.filter { it.groupValues[2] in setOf("ErrorCode", "Cause", "Solution") }
            .map { it.groupValues[3].unescape() }
        val docLines = docs.map { it.groupValues[2].unescape().take(120) }
        val citations = (facts.map { it.groupValues[1] to "GRAPH" } + docs.map { it.groupValues[1] to "DOC" })
            .joinToString(",") { (id, type) -> """{"id":"$id","type":"$type"}""" }
        val body = (factLines + docLines).joinToString(" / ").replace("\"", "'")
        val answer = if (body.isEmpty()) "확인이 필요합니다." else "근거 기반 안내입니다. $body"
        return """{"answer":"$answer","citations":[$citations],"used_solution_ids":[],"needs_escalation":false,"confidence":0.8,"missing_info":[]}"""
    }

    private fun String.unescape() = replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
}

/** Anthropic Messages API 호출. 모델 ID는 설정으로 바꾼다. */
@Component
@ConditionalOnProperty("kg.llm.provider", havingValue = "anthropic")
class AnthropicLlmAdapter(
    @Value("\${kg.llm.model:claude-sonnet-5-5}") private val model: String,
    @Value("\${kg.llm.api-key:}") apiKey: String,
    @Value("\${kg.llm.max-tokens:1024}") private val maxTokens: Int,
    private val mapper: ObjectMapper,
) : LlmPort {
    private val client = RestClient.builder()
        .baseUrl("https://api.anthropic.com")
        .defaultHeader("x-api-key", apiKey)
        .defaultHeader("anthropic-version", "2023-06-01")
        .build()

    override fun complete(system: String, user: String): String {
        val body = mapOf(
            "model" to model,
            "max_tokens" to maxTokens,
            "system" to system,
            "messages" to listOf(mapOf("role" to "user", "content" to user)),
        )
        val raw = client.post().uri("/v1/messages")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(String::class.java)
            ?: error("Anthropic 응답이 비어 있다")
        val node = mapper.readTree(raw)
        return node["content"].first { it["type"].asText() == "text" }["text"].asText()
    }
}
