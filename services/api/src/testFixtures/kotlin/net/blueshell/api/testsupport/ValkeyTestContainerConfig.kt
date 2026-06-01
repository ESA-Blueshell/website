package net.blueshell.api.testsupport

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/**
 * Throwaway Valkey for the @SpringBootTest context so the Redis-backed HTTP
 * session path runs under the real prod config. @ServiceConnection wires
 * spring.data.redis.host/port; `name = "redis"` makes Boot pick the Redis
 * connection-details factory for the non-`redis` image name.
 */
@TestConfiguration(proxyBeanMethods = false)
class ValkeyTestContainerConfig {

    @Bean
    @ServiceConnection(name = "redis")
    fun valkeyContainer(): GenericContainer<*> =
        GenericContainer(DockerImageName.parse("valkey/valkey:8-alpine").asCompatibleSubstituteFor("redis"))
            .withExposedPorts(6379)
}
