package com.example.kg.retrieval.graph

/**
 * 템플릿 Cypher 카탈로그. LLM이 쿼리를 생성하지 않는다.
 * 모든 쿼리는 파라미터($name)를 쓰고, 실행은 GRAPH.RO_QUERY로만 한다.
 */
object CypherCatalog {

    /** Q-LINK-1: 오류 코드 정확 매칭 */
    const val FIND_ERROR_CODES = """
        MATCH (e:ErrorCode) WHERE e.code IN ${'$'}codes AND e.status = 'APPROVED'
        RETURN e.id AS id, e.code AS code
    """

    /** Q-GRAPH-1: 오류 코드 → 소속 기능 → 원인 → 조치 → UI 경로 → 에스컬레이션 */
    const val ERROR_RESOLUTION = """
        MATCH (e:ErrorCode) WHERE e.code IN ${'$'}codes AND e.status = 'APPROVED'
        OPTIONAL MATCH (s:Service)-[:HAS_FEATURE]->(f:Feature)-[:EXPOSED_BY]->(:Api)-[:RETURNS]->(e)
        OPTIONAL MATCH (e)-[:INDICATES]->(c:Cause {status: 'APPROVED'})-[r:RESOLVED_BY]->(sol:Solution {status: 'APPROVED'})
          WHERE sol.audience IN ${'$'}audiences
        OPTIONAL MATCH (sol)-[:LOCATED_AT]->(ui:UiPath {status: 'APPROVED'})
        OPTIONAL MATCH (sol)-[:ESCALATES_TO]->(t:Team)
        RETURN e.id AS error_id, e.code AS code, e.user_message AS user_message, e.updated_at AS error_updated_at,
               s.id AS service_id, s.name AS service, f.id AS feature_id, f.name AS feature,
               c.id AS cause_id, c.description AS cause, c.check_method AS check_method,
               sol.id AS solution_id, sol.title AS solution, sol.steps AS solution_steps,
               r.priority AS priority,
               ui.id AS ui_id, ui.platform AS ui_platform, ui.steps AS ui_steps, ui.min_app_version AS ui_min_version,
               t.id AS team_id, t.name AS team_name, t.channel AS team_channel
        ORDER BY code, priority
        LIMIT 100
    """

    /** Q-GRAPH-2: 증상(Issue) → 원인 후보(likelihood 순) → 조치 */
    const val ISSUE_RESOLUTION = """
        MATCH (i:Issue) WHERE i.id IN ${'$'}issueIds AND i.status = 'APPROVED'
        OPTIONAL MATCH (s:Service)-[:HAS_FEATURE]->(f:Feature)-[:MAY_CAUSE]->(i)
        OPTIONAL MATCH (i)-[hc:HAS_CAUSE]->(c:Cause {status: 'APPROVED'})-[r:RESOLVED_BY]->(sol:Solution {status: 'APPROVED'})
          WHERE sol.audience IN ${'$'}audiences
        OPTIONAL MATCH (sol)-[:LOCATED_AT]->(ui:UiPath {status: 'APPROVED'})
        RETURN i.id AS issue_id, i.title AS issue, s.id AS service_id, s.id AS service_id, s.name AS service, f.id AS feature_id, f.name AS feature,
               c.id AS cause_id, c.description AS cause, c.check_method AS check_method, hc.likelihood AS likelihood,
               sol.id AS solution_id, sol.title AS solution, sol.steps AS solution_steps, r.priority AS priority,
               ui.id AS ui_id, ui.platform AS ui_platform, ui.steps AS ui_steps, ui.min_app_version AS ui_min_version
        ORDER BY likelihood DESC, priority
        LIMIT 100
    """

    /** 벡터 kNN 링킹. score는 거리값(작을수록 유사)이다. */
    const val VECTOR_LINK = """
        CALL db.idx.vector.queryNodes('{label}', 'embedding', ${'$'}k, vecf32(${'$'}vec)) YIELD node, score
        WHERE node.status = 'APPROVED'
        RETURN node.id AS id, node.title AS title, score
    """
}
