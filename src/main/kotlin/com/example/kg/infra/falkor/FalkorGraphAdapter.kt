package com.example.kg.infra.falkor

import com.example.kg.domain.model.GraphFact
import com.example.kg.domain.model.GraphFacts
import com.example.kg.domain.model.LinkMethod
import com.example.kg.domain.model.LinkedEntity
import com.example.kg.domain.ontology.CypherLiteral
import com.example.kg.domain.ontology.NodeLabel
import com.example.kg.domain.port.GraphReadPort
import com.example.kg.domain.port.GraphWritePort
import com.example.kg.retrieval.graph.CypherCatalog
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@ConfigurationProperties("kg.falkordb")
data class FalkorProperties(val graphKey: String = "support_kg")

/**
 * FalkorDB 어댑터. Redis 프로토콜의 GRAPH.RO_QUERY(읽기)와 GRAPH.QUERY(쓰기)를 쓴다.
 * 파라미터는 `CYPHER name=value` 접두사로 넘기고, 값은 CypherLiteral로 렌더링한다.
 */
@Component
class FalkorGraphAdapter(
    private val falkor: FalkorClient,
    private val props: FalkorProperties,
) : GraphReadPort, GraphWritePort {

    override fun findErrorCodes(codes: List<String>): List<LinkedEntity> =
        query(readonly = true, CypherCatalog.FIND_ERROR_CODES, mapOf("codes" to codes))
            .map { LinkedEntity(it["id"].toString(), NodeLabel.ERROR_CODE, 1.0, LinkMethod.EXACT) }

    override fun vectorSearch(label: NodeLabel, vector: FloatArray, k: Int): List<LinkedEntity> {
        val cypher = CypherCatalog.VECTOR_LINK.replace("{label}", label.cypher)
        return query(readonly = true, cypher, mapOf("k" to k, "vec" to vector.toList()))
            .map {
                // FalkorDB score는 거리(작을수록 유사). 유사도 = 1 - 거리로 바꿔 다른 점수와 맞춘다.
                val similarity = 1.0 - (it["score"] as Number).toDouble()
                LinkedEntity(it["id"].toString(), label, similarity, LinkMethod.VECTOR)
            }
    }

    override fun errorResolution(codes: List<String>, audiences: List<String>): GraphFacts =
        toFacts(query(readonly = true, CypherCatalog.ERROR_RESOLUTION, mapOf("codes" to codes, "audiences" to audiences)))

    override fun issueResolution(issueIds: List<String>, audiences: List<String>): GraphFacts =
        toFacts(query(readonly = true, CypherCatalog.ISSUE_RESOLUTION, mapOf("issueIds" to issueIds, "audiences" to audiences)))

    override fun execute(cypher: String) {
        falkor.call("GRAPH.QUERY", props.graphKey, cypher)
    }

    /** 결과 행을 키 이름으로 묶는다. 값이 없으면 null이다. */
    private fun query(readonly: Boolean, cypher: String, params: Map<String, Any?>): List<Map<String, Any?>> {
        val prefix = if (params.isEmpty()) "" else "CYPHER " + params.entries.joinToString(" ") { (k, v) -> "$k=${CypherLiteral.render(v)}" } + " "
        val command = if (readonly) "GRAPH.RO_QUERY" else "GRAPH.QUERY"
        val raw = falkor.call(command, props.graphKey, prefix + cypher.trimIndent())
        if (raw.size < 2) return emptyList()
        val header = (raw[0] as List<*>).map { it.toString() }
        val rows = raw[1] as List<*>
        return rows.map { row -> header.zip(row as List<*>).toMap() }
    }

    /** 행들을 사실(GraphFact) 집합으로 바꾼다. 같은 id는 한 번만 넣는다. */
    private fun toFacts(rows: List<Map<String, Any?>>): GraphFacts {
        val facts = LinkedHashMap<String, GraphFact>()
        fun add(id: Any?, type: String, vararg pairs: Pair<String, Any?>) {
            val key = id?.toString() ?: return
            facts.putIfAbsent(key, GraphFact(key, type, pairs.toMap().filterValues { it != null }))
        }
        rows.forEach { r ->
            add(r["error_id"], "ErrorCode", "code" to r["code"], "user_message" to r["user_message"], "updated_at" to r["error_updated_at"])
            add(r["service_id"], "Service", "name" to r["service"])
            add(r["feature_id"], "Feature", "name" to r["feature"])
            add(r["cause_id"], "Cause", "description" to r["cause"], "check_method" to r["check_method"], "likelihood" to r["likelihood"])
            add(r["solution_id"], "Solution", "title" to r["solution"], "steps" to r["solution_steps"])
            add(r["ui_id"], "UiPath", "platform" to r["ui_platform"], "steps" to r["ui_steps"], "min_app_version" to r["ui_min_version"])
            add(r["team_id"], "Team", "name" to r["team_name"], "channel" to r["team_channel"])
            add(r["issue_id"], "Issue", "title" to r["issue"])
        }
        return GraphFacts(facts.values.toList())
    }
}
