package net.blueshell.api.platform.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.Cache
import org.springframework.cache.annotation.CachingConfigurer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.interceptor.CacheErrorHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import java.time.Duration

/**
 * Valkey-backed cache. Caches the per-request user-principal lookup that
 * JwtAuthFilter runs on every authenticated request. Short TTL bounds
 * staleness on role/enabled changes. Disabled under the `test` profile
 * (`spring.cache.type=none`) so DB resets between tests are never masked.
 */
@Configuration
@EnableCaching
@Profile("!test")
class CacheConfig(
    @param:Value($$"${cache.ttl:5m}")
    private val ttl: Duration,
) : CachingConfigurer {
    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory): RedisCacheManager =
        RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(
                // Pin value (de)serialization to the classloader that loaded this
                // config bean. Cached values (notably the UserPrincipal that
                // JwtAuthFilter reads on every authenticated request) are
                // JDK-serialized into Valkey and outlive a process restart. The
                // default JDK deserializer resolves classes with the "latest
                // user-defined classloader on the stack", which under Spring Boot
                // DevTools is the base loader — not the RestartClassLoader that
                // reloaded the application classes. A cached principal then comes
                // back as a foreign UserPrincipal type and every authenticated
                // call 500s with a ClassCastException. This config is itself
                // reloaded under the current RestartClassLoader, so its
                // classloader always matches the running code; in production (no
                // DevTools) it is the single application loader, so behaviour is
                // unchanged.
                RedisCacheConfiguration.defaultCacheConfig(javaClass.classLoader)
                    .entryTtl(ttl)
                    .disableCachingNullValues(),
            )
            .build()

    /**
     * The cache is an optimisation, not a source of truth. If Valkey is
     * unreachable, a cache get/put must not abort the request — otherwise
     * every authenticated call (which caches its principal lookup) turns into
     * a 500. Log and swallow cache errors so the call falls through to the
     * database instead.
     */
    override fun errorHandler(): CacheErrorHandler = ResilientCacheErrorHandler()
}

private class ResilientCacheErrorHandler : CacheErrorHandler {
    private val log = LoggerFactory.getLogger(ResilientCacheErrorHandler::class.java)

    override fun handleCacheGetError(exception: RuntimeException, cache: Cache, key: Any) {
        log.warn("Cache GET failed on '{}' (key={}); falling back to source", cache.name, key, exception)
    }

    override fun handleCachePutError(exception: RuntimeException, cache: Cache, key: Any, value: Any?) {
        log.warn("Cache PUT failed on '{}' (key={}); value not cached", cache.name, key, exception)
    }

    override fun handleCacheEvictError(exception: RuntimeException, cache: Cache, key: Any) {
        log.warn("Cache EVICT failed on '{}' (key={})", cache.name, key, exception)
    }

    override fun handleCacheClearError(exception: RuntimeException, cache: Cache) {
        log.warn("Cache CLEAR failed on '{}'", cache.name, exception)
    }
}
