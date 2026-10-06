package com.example.kg.domain.ontology

/**
 * 값을 Cypher 리터럴로 바꾼다. 레이블·관계 이름은 enum에서만 오므로 여기서는 값만 다룬다.
 * FalkorDB의 `CYPHER name=value` 형식 파라미터 문자열을 만들 때도 쓴다.
 */
object CypherLiteral {
    fun render(value: Any?): String = when (value) {
        null -> "null"
        is String -> "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"
        is Boolean, is Int, is Long -> value.toString()
        is Double -> if (value.isFinite()) value.toString() else "null"
        is Float -> if (value.isFinite()) value.toString() else "null"
        is FloatArray -> value.joinToString(prefix = "[", postfix = "]") { it.toString() }
        is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { render(it) }
        is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { render(it) }
        is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (k, v) -> "$k: ${render(v)}" }
        else -> render(value.toString())
    }
}
