package net.blueshell.api.testsupport

import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/**
 * One throwaway Valkey for the unit tests of what stores its state there, with no Spring context
 * around it: the rules those classes keep are Lua and hash writes, which a mock would only echo.
 */
object UnitValkey {
    private val container: GenericContainer<*> by lazy {
        GenericContainer(DockerImageName.parse("valkey/valkey:8-alpine"))
            .withExposedPorts(6379)
            .also { it.start() }
    }

    val template: StringRedisTemplate by lazy {
        val factory = LettuceConnectionFactory(RedisStandaloneConfiguration(container.host, container.getMappedPort(6379)))
        factory.afterPropertiesSet()
        factory.start()
        StringRedisTemplate(factory).also { it.afterPropertiesSet() }
    }

    fun flush() {
        template.execute { connection -> connection.serverCommands().flushDb() }
    }
}
