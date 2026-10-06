package com.example.kg.ingestion.extract

import com.example.kg.domain.port.LlmPort
import com.example.kg.domain.ontology.NodeLabel
import com.example.kg.ingestion.validate.TripleValidator
import com.example.kg.ingestion.validate.ValidationOutcome
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Component

/** 문서 청크 → LLM 추출 → 검증. 결과는 항상 DRAFT다. */
@Component
class TripleExtractor(
    private val llm: LlmPort,
    private val mapper: ObjectMapper,
    private val validator: TripleValidator,
) {
    fun extract(
        title: String,
        sectionPath: String,
        serviceHint: String,
        chunk: String,
        knownLabels: Map<String, NodeLabel> = emptyMap(),
    ): ValidationOutcome {
        val candidates = knownLabels.map { (id, label) -> mapOf("id" to id, "label" to label.cypher) }
        val user = ExtractionPrompts.user(title, sectionPath, serviceHint, chunk, mapper.writeValueAsString(candidates))
        val raw = llm.complete(ExtractionPrompts.SYSTEM, user)
        val parsed = mapper.readValue<ExtractionResult>(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1))
        return validator.validate(parsed, chunk, knownLabels)
    }
}
