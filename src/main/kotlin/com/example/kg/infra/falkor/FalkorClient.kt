package com.example.kg.infra.falkor

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.output.NestedMultiOutput
import io.lettuce.core.protocol.CommandArgs
import io.lettuce.core.protocol.ProtocolKeyword
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * FalkorDB 전용 Redis 클라이언트.
 * Spring Data Redis의 `execute()`는 정수가 섞인 중첩 응답을 파싱하지 못하므로(ByteArrayOutput),
 * Lettuce의 NestedMultiOutput으로 직접 디스패치한다. 응답은 String/Long/List로 온다.
 */
@Component
class FalkorClient(
    @Value("\${spring.data.redis.host:localhost}") host: String,
    @Value("\${spring.data.redis.port:6380}") port: Int,
) {
    private val client: RedisClient = RedisClient.create(RedisURI.Builder.redis(host, port).build())
    private val connection: StatefulRedisConnection<String, String> = client.connect(StringCodec.UTF8)

    /** GRAPH.RO_QUERY / GRAPH.QUERY 를 실행하고 원시 응답을 돌려준다. */
    fun call(command: String, graphKey: String, cypher: String): List<*> {
        // 인자를 명시적 UTF-8 바이트로 넘긴다. 문자열 add()는 한글 질의를 깨뜨릴 수 있다.
        val args = CommandArgs(StringCodec.UTF8)
            .add(graphKey.toByteArray(Charsets.UTF_8))
            .add(cypher.toByteArray(Charsets.UTF_8))
        val result = connection.sync().dispatch(Keyword(command), NestedMultiOutput(StringCodec.UTF8), args)
        return result ?: emptyList<Any>()
    }

    @PreDestroy
    fun close() {
        connection.close()
        client.shutdown()
    }

    private class Keyword(private val label: String) : ProtocolKeyword {
        override fun getBytes(): ByteArray = label.toByteArray(Charsets.UTF_8)
        override fun name(): String = label
    }
}
