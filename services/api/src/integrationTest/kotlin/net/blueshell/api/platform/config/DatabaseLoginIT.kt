package net.blueshell.api.platform.config

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import java.time.Duration
import javax.sql.DataSource

/**
 * The api logs in to MariaDB with a login Vault leases it, and keeps working past the
 * role's max_ttl; the migration logs in as the schema's stable owner (api ADR-033). A real
 * MariaDB and a real Vault database engine, with a 20 second max_ttl so a rotation and
 * the revocation of the login before it both happen inside the test.
 */
class DatabaseLoginIT {
    @Test
    fun `the api connects with a leased login and keeps connecting after Vault drops it`() {
        boot("prod", apiToken).use { context ->
            val first = currentUser(context)
            assertThat(first).startsWith("v-")

            await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(2)).untilAsserted {
                assertThat(currentUser(context)).startsWith("v-").isNotEqualTo(first)
            }
            // Past the first lease's max_ttl, so Vault has dropped that user.
            Thread.sleep(MAX_TTL.plusSeconds(2).toMillis())
            assertThat(currentUser(context)).startsWith("v-")
        }
    }

    @Test
    fun `the migration connects as the schema owner, so what it creates outlives any lease`() {
        boot("prod,migrate", migrateToken).use { context ->
            assertThat(currentUser(context)).isEqualTo(OWNER)
        }
    }

    private fun currentUser(context: ConfigurableApplicationContext): String =
        context.getBean(DataSource::class.java).connection.use { connection ->
            connection.createStatement().executeQuery("SELECT CURRENT_USER()").use { rows ->
                rows.next()
                rows.getString(1).substringBefore('@')
            }
        }

    private fun boot(
        profiles: String,
        token: String,
    ): ConfigurableApplicationContext =
        SpringApplicationBuilder(DatabaseOnly::class.java).run(
            // application.yaml sets the servlet type, which outranks the builder.
            "--spring.main.web-application-type=none",
            "--spring.profiles.active=$profiles",
            "--spring.cloud.vault.uri=${vault.uri}",
            "--spring.cloud.vault.authentication=TOKEN",
            "--spring.cloud.vault.token=$token",
            "--spring.cloud.vault.config.lifecycle.expiry-threshold=5s",
            "--spring.cloud.vault.config.lifecycle.min-renewal=1s",
            "--spring.datasource.url=jdbc:mariadb://${mariadb.host}:${mariadb.getMappedPort(3306)}/$SCHEMA",
            "--spring.liquibase.enabled=false",
        )

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(DataSourceAutoConfiguration::class)
    @Import(DatabaseLoginRotation::class)
    class DatabaseOnly

    companion object {
        private const val SCHEMA = "blueshell"
        private const val OWNER = "blueshell"
        private const val OWNER_PASSWORD = "owner-password"
        private const val ROOT_PASSWORD = "root-password"
        private val MAX_TTL: Duration = Duration.ofSeconds(20)

        private val network = Network.newNetwork()
        private val mariadb: GenericContainer<*> =
            GenericContainer("mariadb:11.4")
                .withNetwork(network)
                .withNetworkAliases("mariadb")
                .withEnv("MARIADB_ROOT_PASSWORD", ROOT_PASSWORD)
                .withEnv("MARIADB_DATABASE", SCHEMA)
                .withEnv("MARIADB_USER", OWNER)
                .withEnv("MARIADB_PASSWORD", OWNER_PASSWORD)
                .withExposedPorts(3306)
                .waitingFor(Wait.forLogMessage(".*ready for connections.*", 2))
        private val vault = VaultDevServer(network)
        private lateinit var apiToken: String
        private lateinit var migrateToken: String

        @JvmStatic
        @BeforeAll
        fun seed() {
            mariadb.start()
            vault.start()
            vault.put("secret/api", "app.two-factor.key=two-factor-from-vault")
            vault.put("secret/platform/mail", "account.api=smtp", "account.bounce=imap")
            vault.put("secret/platform/mariadb", "user=$OWNER", "password=$OWNER_PASSWORD")
            vault.vault("secrets", "enable", "database")
            vault.vault(
                "write",
                "database/config/mariadb",
                "plugin_name=mysql-database-plugin",
                "allowed_roles=api",
                "connection_url={{username}}:{{password}}@tcp(mariadb:3306)/",
                "username=root",
                "password=$ROOT_PASSWORD",
            )
            vault.vault(
                "write",
                "database/roles/api",
                "db_name=mariadb",
                "default_ttl=10s",
                "max_ttl=${MAX_TTL.seconds}s",
                "creation_statements=CREATE USER '{{name}}'@'%' IDENTIFIED BY '{{password}}'; " +
                    "GRANT ALL ON $SCHEMA.* TO '{{name}}'@'%';",
            )
            apiToken = vault.tokenFor("api")
            migrateToken = vault.tokenFor("migrate")
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            vault.close()
            mariadb.stop()
            network.close()
        }
    }
}
