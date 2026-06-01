package net.blueshell.api.platform.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.EnableCaching
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
) {
    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory): RedisCacheManager =
        RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(ttl)
                    .disableCachingNullValues(),
            )
            .build()
}
