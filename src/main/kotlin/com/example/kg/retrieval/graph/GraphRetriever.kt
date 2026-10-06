package com.example.kg.retrieval.graph

import com.example.kg.domain.model.AnalyzedQuery
import com.example.kg.domain.model.BranchResult
import com.example.kg.domain.model.GraphFacts
import com.example.kg.domain.model.Intent
import com.example.kg.domain.model.LinkedEntity
import com.example.kg.domain.ontology.NodeLabel
import com.example.kg.domain.port.GraphReadPort
import org.springframework.stereotype.Component

/** 링킹 결과와 의도에 맞는 템플릿 쿼리를 골라 실행한다. */
@Component
class GraphRetriever(private val graph: GraphReadPort) {

    fun retrieve(q: AnalyzedQuery, linked: List<LinkedEntity>, audiences: List<String>): GraphFacts {
        val codes = (q.errorCodes + linked.filter { it.label == NodeLabel.ERROR_CODE }.map { it.id.removePrefix("err:") })
            .distinct()
        if (codes.isNotEmpty() && q.intent == Intent.ERROR_RESOLUTION) {
            return graph.errorResolution(codes, audiences)
        }
        val issueIds = linked.filter { it.label == NodeLabel.ISSUE }.map { it.id }
        if (issueIds.isNotEmpty()) {
            return graph.issueResolution(issueIds, audiences)
        }
        return GraphFacts.EMPTY
    }
}
