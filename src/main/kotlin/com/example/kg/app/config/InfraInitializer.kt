package com.example.kg.app.config

import com.example.kg.domain.port.GraphWritePort
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * 기동 시 FalkorDB 인덱스를 만든다. 이미 있으면 오류를 무시한다.
 * 설계서 2.2의 range index와 vector index를 그대로 쓴다.
 */
@Component
class FalkorSchemaInitializer(private val graph: GraphWritePort) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    private val statements = listOf(
        "CREATE INDEX FOR (n:Service) ON (n.id)",
        "CREATE INDEX FOR (n:Feature) ON (n.id)",
        "CREATE INDEX FOR (n:Api) ON (n.id)",
        "CREATE INDEX FOR (n:ErrorCode) ON (n.id)",
        "CREATE INDEX FOR (n:ErrorCode) ON (n.code)",
        "CREATE INDEX FOR (n:Issue) ON (n.id)",
        "CREATE INDEX FOR (n:Cause) ON (n.id)",
        "CREATE INDEX FOR (n:Solution) ON (n.id)",
        "CREATE INDEX FOR (n:UiPath) ON (n.id)",
        "CREATE INDEX FOR (n:Team) ON (n.id)",
        "CREATE VECTOR INDEX FOR (n:Issue) ON (n.embedding) OPTIONS {dimension:1024, similarityFunction:'cosine'}",
        "CREATE VECTOR INDEX FOR (n:Feature) ON (n.embedding) OPTIONS {dimension:1024, similarityFunction:'cosine'}",
    )

    override fun run(args: ApplicationArguments) {
        statements.forEach { stmt ->
            try {
                graph.execute(stmt)
            } catch (e: Exception) {
                log.info("인덱스 생성 건너뜀 ({}): {}", e.message?.take(80), stmt)
            }
        }
        // 수동 승인된 시드(설계서 2.4). MERGE라서 여러 번 실행해도 안전하다.
        ClassPathResource("graph/seed.cypher").inputStream.bufferedReader().readText()
            .split(";").map { it.trim() }.filter { it.isNotEmpty() }
            .forEach { stmt ->
                try {
                    graph.execute(stmt)
                } catch (e: Exception) {
                    log.warn("시드 구문 실패: {} / {}", e.message, stmt.take(120), e)
                }
            }
        log.info("FalkorDB 인덱스와 시드 적용 완료")
    }
}
