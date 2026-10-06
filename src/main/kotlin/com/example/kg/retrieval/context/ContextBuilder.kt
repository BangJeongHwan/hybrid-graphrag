package com.example.kg.retrieval.context

import com.example.kg.domain.model.AnalyzedQuery
import com.example.kg.domain.model.FusedContext
import com.example.kg.domain.model.GraphFact
import org.springframework.stereotype.Component

/**
 * 최종 LLM 입력을 만든다.
 * 결정적으로 계산할 수 있는 판단(앱 버전 비교 등)은 LLM에 맡기지 않고 <notes>에 미리 넣는다.
 */
@Component
class ContextBuilder {

    fun systemPrompt(): String = SYSTEM_PROMPT

    fun userPrompt(q: AnalyzedQuery, ctx: FusedContext, channel: String, appVersion: String?): String = buildString {
        appendLine("""<request channel="$channel" intent="${q.intent}"${appVersion?.let { " app_version=\"$it\"" } ?: ""}>""")
        appendLine("  <question>${escape(q.masked)}</question>")
        appendLine("</request>")
        appendLine()
        appendLine("<graph_facts>")
        ctx.facts.facts.forEach { appendLine(renderFact(it)) }
        appendLine("</graph_facts>")
        appendLine()
        appendLine("<retrieved_docs>")
        ctx.docs.forEach {
            appendLine("""  <doc id="${it.chunkId}" title="${escape(it.title)}" updated_at="${it.updatedAt}">""")
            appendLine("    ${escape(it.content)}")
            appendLine("  </doc>")
        }
        appendLine("</retrieved_docs>")
        appendLine()
        appendLine("<conflicts>")
        ctx.conflicts.forEach { appendLine("""  <conflict fact="${it.factId}" doc="${it.chunkId}">${it.reason}</conflict>""") }
        appendLine("</conflicts>")
        appendLine()
        appendLine("<notes>")
        if (ctx.degraded.isNotEmpty()) appendLine("  degraded=${ctx.degraded.joinToString(",")} : 구체 조치·경로는 단정하지 말 것")
        versionNote(appVersion, ctx.facts.facts)?.let { appendLine("  $it") }
        appendLine("</notes>")
    }

    /** app_version < min_app_version 이면 업데이트 안내를 미리 계산해 넣는다. */
    private fun versionNote(appVersion: String?, facts: List<GraphFact>): String? {
        if (appVersion == null) return null
        val minVersions = facts.filter { it.type == "UiPath" }.mapNotNull { it.props["min_app_version"] as? String }
        val tooOld = minVersions.any { compareVersions(appVersion, it) < 0 }
        return if (tooOld) "app_version($appVersion) < min_app_version: 앱 업데이트 안내가 필요하다" else null
    }

    private fun renderFact(f: GraphFact): String {
        val attrs = f.props
            .filterKeys { it !in HIDDEN_PROPS }
            .entries.joinToString("; ") { (k, v) -> "$k=${escape(v.toString())}" }
        return """  <fact id="${f.id}" type="${f.type}">$attrs</fact>"""
    }

    companion object {
        private val HIDDEN_PROPS = setOf("updated_at", "embedding", "status")

        fun compareVersions(a: String, b: String): Int {
            val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
            val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val diff = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
                if (diff != 0) return diff
            }
            return 0
        }

        private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        val SYSTEM_PROMPT = """
            너는 서비스 문의 대응 어시스턴트다. 주어진 근거를 정확하게 '설명'하며, 근거에 없는 사실을 만들지 않는다.

            ## 근거의 우선순위
            1. <graph_facts>: 검증·승인된 구조적 사실(Ground Truth). 오류 코드, 원인, 조치 단계, 화면 경로, 담당 팀은 여기 있는 내용만 쓴다.
            2. <retrieved_docs>: 참고 문서. 설명을 보충할 때만 쓴다.
            3. 둘이 충돌하면 <graph_facts>를 따른다. <conflicts>에 기재된 문서 내용은 언급하지 않는다.

            ## 절대 규칙
            - 근거에 없는 오류 코드, 메뉴 이름, 화면 경로, 수치, 연락처를 쓰지 않는다.
            - 화면 경로는 ui_steps 순서 그대로 'A > B > C' 형식으로 쓴다.
            - 원인이 여러 개면 likelihood 순으로 제시하고, 확인 방법(check_method)을 먼저 안내한다.
            - 근거로 답할 수 없는 부분은 "확인이 필요합니다"라고 쓰고, 담당 팀이 있으면 안내한다.
            - 고객 채널(channel=CUSTOMER)에서는 내부 시스템명과 API 경로를 노출하지 않는다.

            ## 출력 형식 (JSON만 출력)
            {"answer": "...", "citations": [{"id": "...", "type": "GRAPH|DOC"}], "used_solution_ids": ["..."],
             "needs_escalation": false, "confidence": 0.0, "missing_info": []}
        """.trimIndent()
    }
}
