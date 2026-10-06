package com.example.kg.support

import com.example.kg.domain.model.DocChunk
import com.example.kg.domain.model.GraphFact
import com.example.kg.domain.model.GraphFacts
import com.example.kg.domain.model.LinkMethod
import com.example.kg.domain.model.LinkedEntity
import com.example.kg.domain.ontology.NodeLabel
import com.example.kg.domain.port.GraphReadPort
import com.example.kg.domain.port.LlmPort
import com.example.kg.domain.port.SearchQuery
import com.example.kg.domain.port.VectorSearchPort

/** 결제 카드 등록 오류 한 건에 대한 그래프 사실 묶음. */
object Fixtures {
    val errorFacts = GraphFacts(
        listOf(
            GraphFact("err:PAY-4031", "ErrorCode", mapOf("code" to "PAY-4031", "user_message" to "카드사에서 승인이 거절되었습니다", "updated_at" to 2000L)),
            GraphFact("svc:payment", "Service", mapOf("name" to "결제")),
            GraphFact("cause:payment:issuer-declined", "Cause", mapOf("description" to "카드사 차단", "check_method" to "카드사 앱에서 온라인 결제 허용 여부 확인")),
            GraphFact("sol:payment:enable-online-pay", "Solution", mapOf("title" to "카드사 온라인 결제 허용", "steps" to listOf("카드사 앱 실행", "온라인 결제 허용 설정"), "audience" to "CUSTOMER")),
            GraphFact("ui:payment:card-manage:app", "UiPath", mapOf("steps" to listOf("마이페이지", "결제수단 관리", "카드 추가"), "min_app_version" to "5.2.0")),
            GraphFact("team:payment-ops", "Team", mapOf("name" to "결제운영팀")),
        ),
    )
}

class FakeGraph(
    private val errorFacts: GraphFacts = Fixtures.errorFacts,
    private val fail: Boolean = false,
) : GraphReadPort {
    override fun findErrorCodes(codes: List<String>): List<LinkedEntity> =
        codes.map { LinkedEntity("err:$it", NodeLabel.ERROR_CODE, 1.0, LinkMethod.EXACT) }

    override fun vectorSearch(label: NodeLabel, vector: FloatArray, k: Int): List<LinkedEntity> = emptyList()

    override fun errorResolution(codes: List<String>, audiences: List<String>): GraphFacts {
        if (fail) error("FalkorDB 연결 실패")
        return errorFacts
    }

    override fun issueResolution(issueIds: List<String>, audiences: List<String>): GraphFacts = GraphFacts.EMPTY
}

class FakeVector(private val chunks: List<DocChunk> = emptyList()) : VectorSearchPort {
    override fun hybridSearch(query: SearchQuery): List<DocChunk> = chunks
}

/** 응답을 순서대로 돌려주는 LLM. 호출 횟수도 센다. */
class ScriptedLlm(private val responses: List<String>) : LlmPort {
    var calls = 0
    override fun complete(system: String, user: String): String =
        responses[minOf(calls++, responses.size - 1)]
}

fun chunk(
    id: String = "doc:faq-payment#1",
    content: String = "카드 등록 오류 PAY-4031 안내",
    updatedAt: Long = 3000L,
    entityIds: List<String> = listOf("err:PAY-4031"),
) = DocChunk(
    chunkId = id, docId = "doc:faq-payment", title = "결제 FAQ", sectionPath = listOf("카드 등록"),
    serviceIds = listOf("svc:payment"), entityIds = entityIds, audience = "CUSTOMER",
    updatedAt = updatedAt, content = content, score = 0.5,
)
