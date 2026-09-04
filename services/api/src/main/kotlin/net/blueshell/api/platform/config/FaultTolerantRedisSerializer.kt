package net.blueshell.api.platform.config

import org.slf4j.LoggerFactory
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializer

/**
 * A [RedisSerializer] that never fails a read on a value it cannot deserialize.
 *
 * A session written by an earlier deploy can stop deserializing — a changed `serialVersionUID`,
 * a removed field, a moved class — and a throwing serializer 500s every request carrying one
 * until the session TTL lapses. A failed read logs and returns null instead, so the value reads
 * as absent and `JwtAuthFilter` rebuilds the security context from the JWT cookie; writes always
 * delegate. The delegate is pinned to this class's classloader, as in [CacheConfig]: under DevTools the
 * reloaded classes live in the RestartClassLoader and would otherwise resolve against the base.
 */
class FaultTolerantRedisSerializer(
    private val delegate: RedisSerializer<Any> =
        JdkSerializationRedisSerializer(FaultTolerantRedisSerializer::class.java.classLoader),
) : RedisSerializer<Any> {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun serialize(value: Any?): ByteArray = delegate.serialize(value)

    override fun deserialize(bytes: ByteArray?): Any? =
        try {
            delegate.deserialize(bytes)
        } catch (ex: Exception) {
            log.warn(
                "Discarding an unreadable Redis value (likely written by a previous deploy); treating it as absent",
                ex,
            )
            null
        }
}
