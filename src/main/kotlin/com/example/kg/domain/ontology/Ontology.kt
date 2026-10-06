package com.example.kg.domain.ontology

/** 그래프 노드 레이블. 설계서 2장과 1:1로 대응한다. */
enum class NodeLabel(val cypher: String) {
    SERVICE("Service"),
    FEATURE("Feature"),
    API("Api"),
    ERROR_CODE("ErrorCode"),
    ISSUE("Issue"),
    CAUSE("Cause"),
    SOLUTION("Solution"),
    UI_PATH("UiPath"),
    TEAM("Team"),
    DOCUMENT("Document"),
}

/** 관계 타입. 쿼리와 프롬프트에서 문자열을 직접 쓰지 않도록 enum으로 고정한다. */
enum class RelType(val cypher: String) {
    HAS_FEATURE("HAS_FEATURE"),
    EXPOSED_BY("EXPOSED_BY"),
    RETURNS("RETURNS"),
    MAY_CAUSE("MAY_CAUSE"),
    HAS_ERROR_CODE("HAS_ERROR_CODE"),
    HAS_CAUSE("HAS_CAUSE"),
    INDICATES("INDICATES"),
    RESOLVED_BY("RESOLVED_BY"),
    LOCATED_AT("LOCATED_AT"),
    ESCALATES_TO("ESCALATES_TO"),
    OWNED_BY("OWNED_BY"),
    DEPENDS_ON("DEPENDS_ON"),
    DESCRIBED_IN("DESCRIBED_IN"),
}

/**
 * LLM이 추출한 트리플이 허용되는지 판단하는 화이트리스트.
 * (src 레이블, 관계, dst 레이블) 조합만 DRAFT로 들어갈 수 있다.
 */
object AllowedTriples {
    private val allowed: Set<Triple<NodeLabel, RelType, NodeLabel>> = setOf(
        Triple(NodeLabel.SERVICE, RelType.HAS_FEATURE, NodeLabel.FEATURE),
        Triple(NodeLabel.FEATURE, RelType.EXPOSED_BY, NodeLabel.API),
        Triple(NodeLabel.API, RelType.RETURNS, NodeLabel.ERROR_CODE),
        Triple(NodeLabel.FEATURE, RelType.MAY_CAUSE, NodeLabel.ISSUE),
        Triple(NodeLabel.ISSUE, RelType.HAS_ERROR_CODE, NodeLabel.ERROR_CODE),
        Triple(NodeLabel.ISSUE, RelType.HAS_CAUSE, NodeLabel.CAUSE),
        Triple(NodeLabel.ERROR_CODE, RelType.INDICATES, NodeLabel.CAUSE),
        Triple(NodeLabel.CAUSE, RelType.RESOLVED_BY, NodeLabel.SOLUTION),
        Triple(NodeLabel.SOLUTION, RelType.LOCATED_AT, NodeLabel.UI_PATH),
        Triple(NodeLabel.SOLUTION, RelType.ESCALATES_TO, NodeLabel.TEAM),
        Triple(NodeLabel.SERVICE, RelType.OWNED_BY, NodeLabel.TEAM),
        Triple(NodeLabel.FEATURE, RelType.DEPENDS_ON, NodeLabel.FEATURE),
        Triple(NodeLabel.FEATURE, RelType.DESCRIBED_IN, NodeLabel.DOCUMENT),
        Triple(NodeLabel.ISSUE, RelType.DESCRIBED_IN, NodeLabel.DOCUMENT),
        Triple(NodeLabel.SOLUTION, RelType.DESCRIBED_IN, NodeLabel.DOCUMENT),
    )

    fun isAllowed(src: NodeLabel, rel: RelType, dst: NodeLabel): Boolean =
        Triple(src, rel, dst) in allowed
}

/**
 * 결정적 canonical id 규칙: `{label 약어}:{service}:{kebab-slug}`.
 * 오류 코드는 전사에서 유일하므로 `err:{CODE}` 형식을 쓴다.
 */
object CanonicalId {
    private val slugRegex = Regex("[^a-z0-9가-힣]+")

    fun slug(text: String): String =
        text.trim().lowercase().replace(slugRegex, "-").trim('-')

    fun errorCode(code: String): String = "err:${code.uppercase()}"

    fun of(prefix: String, service: String, name: String): String =
        "$prefix:${slug(service)}:${slug(name)}"
}
