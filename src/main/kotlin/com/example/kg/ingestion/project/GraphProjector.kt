package com.example.kg.ingestion.project

import com.example.kg.domain.model.KgBatch
import com.example.kg.domain.model.KgEdge
import com.example.kg.domain.model.KgNode
import com.example.kg.domain.ontology.CypherLiteral
import com.example.kg.domain.port.GraphWritePort
import org.springframework.stereotype.Component

/**
 * 승인된 지식을 FalkorDB에 idempotent하게 투영한다(MERGE + SET).
 * 레이블·관계는 enum의 cypher 이름만 쓰고, 값은 리터럴로 렌더링한다.
 */
@Component
class GraphProjector(private val graph: GraphWritePort) {

    fun project(batch: KgBatch) {
        batch.nodes.forEach { graph.execute(nodeCypher(it)) }
        batch.edges.forEach { graph.execute(edgeCypher(it)) }
    }

    internal fun nodeCypher(n: KgNode): String {
        val props = n.props.toMutableMap()
        val vector = props.remove("embedding") as? FloatArray
        val set = buildString {
            append("MERGE (n:${n.label.cypher} {id: ${CypherLiteral.render(n.id)}}) ")
            append("SET n += ${CypherLiteral.render(props)}")
            if (vector != null) append(", n.embedding = vecf32(${CypherLiteral.render(vector)})")
        }
        return set
    }

    internal fun edgeCypher(e: KgEdge): String = buildString {
        append("MATCH (a:${e.srcLabel.cypher} {id: ${CypherLiteral.render(e.srcId)}}), ")
        append("(b:${e.dstLabel.cypher} {id: ${CypherLiteral.render(e.dstId)}}) ")
        append("MERGE (a)-[r:${e.rel.cypher}]->(b) ")
        if (e.props.isNotEmpty()) append("SET r += ${CypherLiteral.render(e.props)}")
    }
}
