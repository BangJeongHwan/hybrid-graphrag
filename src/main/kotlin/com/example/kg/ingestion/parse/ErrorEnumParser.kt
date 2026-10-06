package com.example.kg.ingestion.parse

import com.example.kg.domain.model.KgBatch
import com.example.kg.domain.model.KgEdge
import com.example.kg.domain.model.KgNode
import com.example.kg.domain.ontology.CanonicalId
import com.example.kg.domain.ontology.NodeLabel
import com.example.kg.domain.ontology.RelType
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * 서비스 CI가 내보낸 오류 코드 JSON을 L0 지식(APPROVED)으로 변환한다. LLM을 쓰지 않는 결정적 파서다.
 *
 * 입력 예:
 * {"service":"payment","version":"2026.10.01","errors":[{"code":"PAY-4031","httpStatus":422,
 *   "name":"CARD_DECLINED_BY_ISSUER","userMessage":"...","operations":[
 *   {"operationId":"registerCard","method":"POST","path":"/v1/cards","feature":"카드 등록"}]}]}
 */
@Component
class ErrorEnumParser(private val mapper: ObjectMapper) {

    fun parse(json: String, nowMillis: Long = Instant.now().toEpochMilli()): KgBatch {
        val export = mapper.readValue(json, ErrorExport::class.java)
        val service = export.service
        val serviceId = "svc:${CanonicalId.slug(service)}"

        val nodes = linkedMapOf<String, KgNode>()
        val edges = mutableListOf<KgEdge>()
        fun node(id: String, label: NodeLabel, props: Map<String, Any?>) {
            nodes.putIfAbsent(id, KgNode(id, label, props + governance(nowMillis, export.version)))
        }

        node(serviceId, NodeLabel.SERVICE, mapOf("id" to serviceId, "name" to service))

        export.errors.forEach { err ->
            val errorId = CanonicalId.errorCode(err.code)
            node(
                errorId, NodeLabel.ERROR_CODE,
                mapOf(
                    "id" to errorId, "code" to err.code.uppercase(), "name" to err.name,
                    "http_status" to err.httpStatus, "message" to err.name, "user_message" to err.userMessage,
                ),
            )
            err.operations.forEach { op ->
                val featureId = CanonicalId.of("feat", service, op.feature)
                val apiId = "api:${CanonicalId.slug(service)}:${op.method.uppercase()}:${op.path}"
                node(featureId, NodeLabel.FEATURE, mapOf("id" to featureId, "name" to op.feature))
                node(apiId, NodeLabel.API, mapOf("id" to apiId, "method" to op.method.uppercase(), "path" to op.path, "operation_id" to op.operationId))
                edges += KgEdge(serviceId, NodeLabel.SERVICE, RelType.HAS_FEATURE, featureId, NodeLabel.FEATURE)
                edges += KgEdge(featureId, NodeLabel.FEATURE, RelType.EXPOSED_BY, apiId, NodeLabel.API)
                edges += KgEdge(apiId, NodeLabel.API, RelType.RETURNS, errorId, NodeLabel.ERROR_CODE, mapOf("http_status" to err.httpStatus))
            }
        }
        return KgBatch(nodes.values.toList(), edges.distinct())
    }

    private fun governance(now: Long, version: String) = mapOf(
        "status" to "APPROVED", "confidence" to 1.0, "source" to "ENUM",
        "source_ref" to version, "version" to 1, "updated_at" to now,
    )
}

data class ErrorExport(
    val service: String = "",
    val version: String = "",
    val errors: List<ErrorItem> = emptyList(),
)

data class ErrorItem(
    val code: String = "",
    val httpStatus: Int = 0,
    val name: String = "",
    val userMessage: String = "",
    val operations: List<OperationItem> = emptyList(),
)

data class OperationItem(
    val operationId: String = "",
    val method: String = "POST",
    val path: String = "",
    val feature: String = "",
)
