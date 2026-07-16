package net.blueshell.api.platform.config

import org.slf4j.LoggerFactory
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializer

/**
 * A [RedisSerializer] that never fails a read on a value it cannot deserialize.
 *
 * Values written by a previous deployment can become unreadable after an update
 * — a changed `serialVersionUID`, a removed field, a class that moved. The
 * default JDK serializer throws in that case, which for the Spring-Session-backed
 * security context means every request carrying an old session 500s until the
 * session's (long) TTL lapses or Valkey is flushed.
 *
 * Here a read failure is logged and returns `null` instead, so the stale value
 * is treated as absent: the session's security context is simply rebuilt from
 * the still-valid JWT cookie by `JwtAuthFilter` and re-saved in the current
 * format. Users stay signed in and no request 500s. Writes always delegate.
 *
 * The delegate is pinned to this class's classloader for the same reason
 * [CacheConfig] is — under Spring Boot DevTools the reloaded classes live in the
 * RestartClassLoader, and the default deserializer would otherwise resolve them
 * with the base loader.
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
