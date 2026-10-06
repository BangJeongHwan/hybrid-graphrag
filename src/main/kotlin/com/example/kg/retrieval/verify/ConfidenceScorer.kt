package com.example.kg.retrieval.verify

import com.example.kg.domain.model.AnswerDraft
import com.example.kg.domain.model.FusedContext
import com.example.kg.domain.model.LinkedEntity
import org.springframework.stereotype.Component

/**
 * 설계서 7.6의 가중합. 가중치는 Golden Set으로 보정하기 전까지 임시값이다.
 */
@Component
class ConfidenceScorer {

    fun score(links: List<LinkedEntity>, ctx: FusedContext, draft: AnswerDraft): Double {
        val linkScore = links.maxOfOrNull { it.score } ?: 0.0
        val coverage = coverage(ctx)
        val topRerank = ctx.docs.maxOfOrNull { it.rerankScore ?: 0.0 } ?: 0.0
        val raw = 0.35 * linkScore +
            0.30 * coverage +
            0.20 * topRerank.coerceIn(0.0, 1.0) +
            0.15 * draft.confidence.coerceIn(0.0, 1.0) -
            (if (ctx.degraded.isNotEmpty()) 0.2 else 0.0) -
            minOf(0.1 * ctx.conflicts.size, 0.3)
        return raw.coerceIn(0.0, 1.0)
    }

    /** 원인·조치·화면 경로 중 근거가 채워진 비율. */
    private fun coverage(ctx: FusedContext): Double {
        val types = ctx.facts.facts.map { it.type }.toSet()
        val hits = listOf("Cause", "Solution", "UiPath").count { it in types }
        return hits / 3.0
    }
}
