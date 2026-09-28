package net.blueshell.api.platform.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Configuration
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import java.io.File

/**
 * The prod profile reads its secrets from Vault KV v2 through Spring Cloud Vault, with
 * no environment variable in between (api ADR-033). A real Vault, because what is
 * under test is the import location, the KV v2 mount detection, the key names and the
 * `api` policy the bootstrap Job writes. A token holding only that policy stands in
 * for the cluster's Kubernetes auth, which grants the same one.
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
            assertThat(property("spring.datasource.username")).isEqualTo("db-user-from-vault")
            assertThat(property("spring.datasource.password")).isEqualTo("db-password-from-vault")
        }
    }

    @Test
    fun `a key missing from Vault is blank in prod, never application yaml's development value`() {
        prodEnvironment { property ->
            assertThat(property("auth.clients.vault.secret")).isEmpty()
        }
    }

    @Test
    fun `the prod profile reads the mail passwords from the path Stalwart shares, by Stalwart's names`() {
        prodEnvironment { property ->
            assertThat(property("spring.mail.password")).isEqualTo("smtp-from-vault")
            assertThat(property("email.bounce.imap.password")).isEqualTo("imap-from-vault")
        }
    }

    private fun prodEnvironment(assertions: ((String) -> String?) -> Unit) {
        SpringApplicationBuilder(NoBeans::class.java)
            .run(
                // application.yaml sets the servlet type, which outranks the builder.
                "--spring.main.web-application-type=none",
                "--spring.profiles.active=prod",
                "--spring.cloud.vault.uri=http://${vault.host}:${vault.getMappedPort(VAULT_PORT)}",
                "--spring.cloud.vault.authentication=TOKEN",
                "--spring.cloud.vault.token=$apiToken",
            ).use { context -> assertions { context.environment.getProperty(it) } }
    }

    @Configuration(proxyBeanMethods = false)
    class NoBeans

    companion object {
        private const val VAULT_PORT = 8200
        private const val ROOT_TOKEN = "root"
        private const val BOOTSTRAP_SCRIPT = "../../platform/cluster/flux/apps/data/vault/bootstrap-auth.sh"

        private lateinit var apiToken: String

        private val vault: GenericContainer<*> =
            GenericContainer("hashicorp/vault:1.21.2")
                .withEnv("VAULT_DEV_ROOT_TOKEN_ID", ROOT_TOKEN)
                .withEnv("VAULT_DEV_LISTEN_ADDRESS", "0.0.0.0:$VAULT_PORT")
                .withEnv("SKIP_SETCAP", "true")
                .withExposedPorts(VAULT_PORT)
                .waitingFor(Wait.forHttp("/v1/sys/health").forStatusCode(200))

        @JvmStatic
        @BeforeAll
        fun seed() {
            vault.start()
            put(
                "secret/api",
                "app.jwt.secret=jwt-from-vault",
                "app.two-factor.key=two-factor-from-vault",
                "brevo.apiKey=brevo-from-vault",
                """google.calendar.serviceAccountJson={"type":"service_account"}""",
                "discord.botToken=discord-from-vault",
                "spring.datasource.username=db-user-from-vault",
                "spring.datasource.password=db-password-from-vault",
            )
            put("secret/platform/mail", "account.api=smtp-from-vault", "account.bounce=imap-from-vault")
            vault.copyFileToContainer(Transferable.of(apiPolicy()), "/tmp/api.hcl")
            vault("policy", "write", "api", "/tmp/api.hcl")
            apiToken = vault("token", "create", "-policy=api", "-field=token").trim()
        }

        /** The policy exactly as the bootstrap Job writes it, so the two cannot drift. */
        private fun apiPolicy(): String {
            val script = File(BOOTSTRAP_SCRIPT).readText()
            val start = script.indexOf("cat <<'EOF' >/tmp/api.hcl\n")
            check(start >= 0) { "no api policy in $BOOTSTRAP_SCRIPT" }
            val body = script.substring(start).substringAfter('\n')
            return body.substringBefore("\nEOF\n") + "\n"
        }

        @JvmStatic
        @AfterAll
        fun stop() = vault.stop()

        private fun put(
            path: String,
            vararg fields: String,
        ) {
            vault("kv", "put", path, *fields)
        }

        private fun vault(vararg args: String): String {
            val result =
                vault.execInContainer(
                    "env",
                    "VAULT_ADDR=http://127.0.0.1:$VAULT_PORT",
                    "VAULT_TOKEN=$ROOT_TOKEN",
                    "vault",
                    *args,
                )
            check(result.exitCode == 0) { "vault ${args.joinToString(" ")} failed: ${result.stderr}" }
            return result.stdout
        }
    }
}
