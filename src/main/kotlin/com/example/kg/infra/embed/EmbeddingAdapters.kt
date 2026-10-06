package com.example.kg.infra.embed

import com.example.kg.domain.port.EmbeddingPort
import com.example.kg.domain.port.LexicalTokenizer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import kotlin.math.sqrt

/**
 * 테스트·오프라인 개발용. 토큰 해시(hashing trick)로 결정적인 벡터를 만든다.
 * 같은 단어를 많이 공유하는 문장일수록 가까워지므로 파이프라인 검증에는 충분하다.
 */
@Component
@ConditionalOnProperty("kg.embedding.provider", havingValue = "fake", matchIfMissing = true)
class FakeEmbeddingAdapter(
    private val tokenizer: LexicalTokenizer,
) : EmbeddingPort {
    override val dimension = 1024

    override fun embed(text: String): FloatArray {
        val v = FloatArray(dimension)
        tokenizer.tokenize(text).forEach { token ->
            val h = token.hashCode()
            val idx = Math.floorMod(h, dimension)
            v[idx] += if (h and 1 == 0) 1f else -1f
        }
        val norm = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0f) for (i in v.indices) v[i] /= norm
        return v
    }
}

/** HuggingFace TEI(Text Embeddings Inference) 서버 호출. POST {base}/embed */
@Component
@ConditionalOnProperty("kg.embedding.provider", havingValue = "tei")
class TeiEmbeddingAdapter(
    @org.springframework.beans.factory.annotation.Value("\${kg.embedding.tei-url}") baseUrl: String,
    @org.springframework.beans.factory.annotation.Value("\${kg.embedding.dimension:1024}") override val dimension: Int,
) : EmbeddingPort {
    private val client = RestClient.builder().baseUrl(baseUrl).build()

    override fun embed(text: String): FloatArray {
        val response = client.post().uri("/embed")
            .body(mapOf("inputs" to text))
            .retrieve()
            .body(Array<FloatArray>::class.java)
            ?: error("TEI 임베딩 응답이 비어 있다")
        return response.first()
    }

    override fun embedAll(texts: List<String>): List<FloatArray> {
        val response = client.post().uri("/embed")
            .body(mapOf("inputs" to texts))
            .retrieve()
            .body(Array<FloatArray>::class.java)
            ?: error("TEI 임베딩 응답이 비어 있다")
        return response.toList()
    }
}
