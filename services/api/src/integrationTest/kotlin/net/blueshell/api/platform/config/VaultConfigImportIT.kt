package net.blueshell.api.platform.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Configuration

/**
 * The prod profile reads its secrets from Vault KV v2 through Spring Cloud Vault, with
 * no environment variable in between (api ADR-033). A real Vault, because what is
 * under test is the import location, the KV v2 mount detection, the key names, the
 * settings the prod profile builds from them and the `api` policy the bootstrap Job writes. A token holding only that policy stands in
 * for the cluster's Kubernetes auth, which grants the same one. The leased database
 * login has its own test, DatabaseLoginIT.
 */
class VaultConfigImportIT {
    @Test
    fun `the prod profile reads every api secret from Vault by its property name`() {
        prodEnvironment { property ->
            assertThat(property("app.jwt.secret")).isEqualTo("jwt-from-vault")
            assertThat(property("app.two-factor.key")).isEqualTo("two-factor-from-vault")
            assertThat(property("brevo.apiKey")).isEqualTo("brevo-from-vault")
            assertThat(property("google.calendar.serviceAccountJson")).isEqualTo("""{"type":"service_account"}""")
            assertThat(property("discord.botToken")).isEqualTo("discord-from-vault")
        }
    }

    @Test
    fun `a key missing from Vault is blank in prod, never application yaml's development value`() {
        prodEnvironment { property ->
            assertThat(property("auth.clients.vault.secret")).isEmpty()
        }
    }

    @Test
    fun `the prod profile sends and polls as the bounce mailbox, with its password from Vault`() {
        prodEnvironment { property ->
            assertThat(property("spring.mail.username")).isEqualTo(BOUNCE_MAILBOX)
            assertThat(property("spring.mail.properties.mail.smtp.from")).isEqualTo(BOUNCE_MAILBOX)
            assertThat(property("spring.mail.password")).isEqualTo("bounce-from-vault")
            assertThat(property("email.bounce.imap.password")).isEqualTo("bounce-from-vault")
        }
    }

    private fun prodEnvironment(assertions: ((String) -> String?) -> Unit) {
        SpringApplicationBuilder(NoBeans::class.java)
            .run(
                *vault.bootArguments("prod", apiToken),
                "--spring.cloud.vault.database.enabled=false",
                // The api Deployment names the bounce mailbox.
                "--EMAIL_BOUNCE_IMAP_USERNAME=$BOUNCE_MAILBOX",
            ).use { context -> assertions { context.environment.getProperty(it) } }
    }

    @Configuration(proxyBeanMethods = false)
    class NoBeans

    companion object {
        private const val BOUNCE_MAILBOX = "bounce@esa-blueshell.nl"
        private val vault = VaultDevServer()
        private lateinit var apiToken: String

        @JvmStatic
        @BeforeAll
        fun seed() {
            vault.start()
            vault.put(
                "secret/api",
                "app.jwt.secret=jwt-from-vault",
                "app.two-factor.key=two-factor-from-vault",
                "brevo.apiKey=brevo-from-vault",
                """google.calendar.serviceAccountJson={"type":"service_account"}""",
                "discord.botToken=discord-from-vault",
            )
            vault.put("secret/platform/mail", "account.bounce=bounce-from-vault")
            apiToken = vault.tokenFor("api")
        }

        @JvmStatic
        @AfterAll
        fun stop() = vault.close()
    }
}
