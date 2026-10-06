package com.example.kg.infra

import com.example.kg.infra.embed.FakeEmbeddingAdapter
import com.example.kg.infra.lexical.NoriTokenizer
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class AdapterTest {
    private val tokenizer = NoriTokenizer()
    private val embedding = FakeEmbeddingAdapter(tokenizer)

    private fun cosine(a: FloatArray, b: FloatArray): Double {
        val dot = a.indices.sumOf { (a[it] * b[it]).toDouble() }
        val na = sqrt(a.sumOf { (it * it).toDouble() })
        val nb = sqrt(b.sumOf { (it * it).toDouble() })
        return dot / (na * nb)
    }

    @Test
    fun `nori splits korean particles so keyword matching works`() {
        val tokens = tokenizer.tokenize("카드를 등록하는데 오류가 떠요")
        assertTrue("카드" in tokens, tokens.toString())
        assertTrue("등록" in tokens, tokens.toString())
    }

    @Test
    fun `fake embedding ranks shared-vocabulary text closer`() {
        val q = embedding.embed("카드 등록 실패")
        val near = embedding.embed("카드 등록 실패 안내 문서")
        val far = embedding.embed("배송 조회 방법")
        assertTrue(cosine(q, near) > cosine(q, far))
    }

    @Test
    fun `fake embedding is deterministic and unit length`() {
        val a = embedding.embed("결제 오류")
        val b = embedding.embed("결제 오류")
        assertTrue(a.contentEquals(b))
        assertTrue(kotlin.math.abs(sqrt(a.sumOf { (it * it).toDouble() }) - 1.0) < 1e-4)
    }
}
