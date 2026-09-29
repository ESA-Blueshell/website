package net.blueshell.api.platform.config

import org.assertj.core.api.Assertions.assertThat
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
import java.sql.DriverManager
import java.time.Duration
import java.time.Instant
import javax.sql.DataSource

/**
 * The api's database login is a Vault lease that the pool keeps current past the role's
 * max_ttl, and the migration logs in as the schema's owner (api ADR-033). A real MariaDB
 * and a real Vault database engine, with a 20 second max_ttl, so a rotation and the drop
 * of the user before it both happen inside the test.
 */
class DatabaseLoginIT {
    @Test
    fun `the api keeps querying through a rotation, past the moment Vault drops its first login`() {
        SpringApplicationBuilder(DatabaseOnly::class.java).run(*apiArguments()).use { context ->
            val first = currentUser(context)
            assertThat(first).startsWith("v-")

            val failures = mutableListOf<Exception>()
            val deadline = Instant.now().plus(MAX_TTL.multipliedBy(3))
            while (userExists(first) && Instant.now().isBefore(deadline)) {
                runCatching { currentUser(context) }.onFailure { failures += it as Exception }
                Thread.sleep(POLL.toMillis())
            }

            assertThat(userExists(first)).describedAs("Vault dropped the first leased user").isFalse()
            assertThat(failures).isEmpty()
            assertThat(currentUser(context)).startsWith("v-").isNotEqualTo(first)
        }
    }

    @Test
    fun `the next release's image boots under the api Deployment that still renders the injector's env`() {
        // What today's pod sets: the bare Vault import, Vault switched on and the rendered keys.
        val injectorEnv =
            arrayOf(
                "--spring.config.import=vault://",
                "--VAULT_ENABLED=true",
                "--JWT_SECRET=from-the-injector",
                "--MYSQL_USER=root",
            )
        SpringApplicationBuilder(DatabaseOnly::class.java).run(*apiArguments(), *injectorEnv).use { context ->
            assertThat(currentUser(context)).startsWith("v-")
            assertThat(context.environment.getProperty("app.two-factor.key")).isEqualTo("two-factor-from-vault")
        }
    }

    @Test
    fun `the migration connects as the schema owner, so what it creates outlives any lease`() {
        SpringApplicationBuilder(DatabaseOnly::class.java)
            .run(*vault.bootArguments("prod,migrate", migrateToken), *databaseArguments())
            .use { context -> assertThat(currentUser(context)).isEqualTo(OWNER) }
    }

    private fun apiArguments() =
        arrayOf(
            *vault.bootArguments("prod", apiToken),
            *databaseArguments(),
            "--spring.cloud.vault.config.lifecycle.expiry-threshold=5s",
            "--spring.cloud.vault.config.lifecycle.min-renewal=1s",
        )

    private fun databaseArguments() =
        arrayOf(
            "--spring.datasource.url=${jdbcUrl()}",
            "--spring.liquibase.enabled=false",
        )

    private fun jdbcUrl() = "jdbc:mariadb://${mariadb.host}:${mariadb.getMappedPort(PORT)}/$SCHEMA"

    private fun currentUser(context: ConfigurableApplicationContext): String =
        context.getBean(DataSource::class.java).connection.use { connection ->
            connection.createStatement().executeQuery("SELECT CURRENT_USER()").use { rows ->
                rows.next()
                rows.getString(1).substringBefore('@')
            }
        }

    private fun userExists(user: String): Boolean =
        DriverManager.getConnection(jdbcUrl(), "root", ROOT_PASSWORD).use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM mysql.user WHERE User = ?").use { statement ->
                statement.setString(1, user)
                statement.executeQuery().use { rows -> rows.next() && rows.getInt(1) > 0 }
            }
        }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(DataSourceAutoConfiguration::class)
    @Import(DatabaseLoginRotation::class)
    class DatabaseOnly

    companion object {
        private const val PORT = 3306
        private const val SCHEMA = "blueshell"
        private const val OWNER = "blueshell"
        private const val OWNER_PASSWORD = "owner-password"
        private const val ROOT_PASSWORD = "root-password"
        private val MAX_TTL: Duration = Duration.ofSeconds(20)
        private val POLL: Duration = Duration.ofMillis(500)

        private val network = Network.newNetwork()
        private val mariadb: GenericContainer<*> =
            GenericContainer("mariadb:11.4")
                .withNetwork(network)
                .withNetworkAliases("mariadb")
                .withEnv("MARIADB_ROOT_PASSWORD", ROOT_PASSWORD)
                .withEnv("MARIADB_DATABASE", SCHEMA)
                .withEnv("MARIADB_USER", OWNER)
                .withEnv("MARIADB_PASSWORD", OWNER_PASSWORD)
                .withExposedPorts(PORT)
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
            vault.cli("secrets", "enable", "database")
            vault.cli(
                "write",
                "database/config/mariadb",
                "plugin_name=mysql-database-plugin",
                "allowed_roles=api",
                "connection_url={{username}}:{{password}}@tcp(mariadb:$PORT)/",
                "username=root",
                "password=$ROOT_PASSWORD",
            )
            vault.cli(
                "write",
                "database/roles/api",
                "db_name=mariadb",
                "default_ttl=10s",
                "max_ttl=${MAX_TTL.seconds}s",
                "creation_statements=CREATE USER '{{name}}'@'%' IDENTIFIED BY '{{password}}'; " +
                    "GRANT SELECT, INSERT, UPDATE, DELETE, CREATE TEMPORARY TABLES ON $SCHEMA.* TO '{{name}}'@'%';",
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
