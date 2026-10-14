package net.blueshell.api.platform.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

/** Hibernate's start-up `HHH10001005` line prints the JDBC URL, and the MariaDB driver puts the login in it. */
class ConnectionInfoLoggingTest {
    private val key = "logging.level.org.hibernate.orm.connections.pooling"
    private val quiet = setOf("WARN", "ERROR", "OFF")

    @Test
    fun `the base configuration keeps the connection info line out`() {
        assertThat(levelIn(resource("application.yaml"))).isIn(quiet)
    }

    @Test
    fun `no profile turns the connection info line back on`() {
        val profiles = PathMatchingResourcePatternResolver().getResources("classpath*:application-*.y*ml")

        assertThat(profiles).isNotEmpty
        profiles.forEach { profile ->
            val level = levelIn(profile)
            if (level != null) assertThat(level).describedAs(profile.filename).isIn(quiet)
        }
    }

    private fun resource(name: String) = PathMatchingResourcePatternResolver().getResource("classpath:$name")

    private fun levelIn(resource: org.springframework.core.io.Resource): String? =
        YamlPropertiesFactoryBean()
            .apply { setResources(resource) }
            .getObject()
            ?.getProperty(key)
            ?.uppercase()
}
