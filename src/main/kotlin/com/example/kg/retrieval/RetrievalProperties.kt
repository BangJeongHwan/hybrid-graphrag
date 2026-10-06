package com.example.kg.retrieval

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("kg.retrieval")
data class RetrievalProperties(
    val graphTimeout: Duration = Duration.ofMillis(800),
    val vectorTimeout: Duration = Duration.ofMillis(800),
    val linkTopK: Int = 5,
    val linkMinSimilarity: Double = 0.75,
    val ambiguityMargin: Double = 0.05,
    val vectorTopK: Int = 30,
    val maxDocs: Int = 5,
    val graphBoost: Double = 0.1,
    val minRerankScore: Double = 0.0,
    val learningThreshold: Double = 0.6,
)
