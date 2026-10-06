package com.example.kg.infra.lexical

import com.example.kg.domain.port.LexicalTokenizer
import org.apache.lucene.analysis.ko.KoreanAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.springframework.stereotype.Component
import java.io.StringReader

/**
 * Lucene Nori로 한국어 형태소를 나눈다. 조사·어미가 떨어져 키워드 매칭이 된다.
 * 분석기는 스레드 안전하므로 하나를 공유한다.
 */
@Component
class NoriTokenizer : LexicalTokenizer {
    private val analyzer = KoreanAnalyzer()

    override fun tokenize(text: String): List<String> {
        val out = mutableListOf<String>()
        analyzer.tokenStream("f", StringReader(text)).use { ts ->
            val term = ts.addAttribute(CharTermAttribute::class.java)
            ts.reset()
            while (ts.incrementToken()) {
                val t = term.toString().lowercase()
                if (t.isNotBlank()) out += t
            }
            ts.end()
        }
        return out
    }
}
