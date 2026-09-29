package net.blueshell.api.platform.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.cloud.context.environment.EnvironmentChangeEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * A key changed in Vault reaches the running api's environment, against a real Vault and
 * the `api` policy (api ADR-033). The poll is called directly rather than waited for.
 */
class VaultKeyRefresherIT {
    @Test
    fun `a Brevo key and a mail password rotated in Vault reach the running api`() {
        SpringApplicationBuilder(RefresherOnly::class.java)
            .run(
                *vault.bootArguments("prod", vault.tokenFor("api")),
                "--spring.cloud.vault.database.enabled=false",
                // An import list that outranks the profile's, as the api Deployment sets until the
                // contract step of api ADR-033. Optional here only because this test runs no database.
                "--spring.config.import=optional:vault://",
                "--VAULT_ENABLED=true",
            ).use { context ->
                vault.put("secret/api", "brevo.apiKey=rotated-brevo", "app.two-factor.key=two-factor")
                vault.put("secret/platform/mail", "account.api=rotated-smtp", "account.bounce=imap")
                context.getBean(VaultKeyRefresher::class.java).refresh()

                assertThat(context.environment.getProperty("brevo.apiKey")).isEqualTo("rotated-brevo")
                assertThat(context.environment.getProperty("spring.mail.password")).isEqualTo("rotated-smtp")
                assertThat(context.getBean(Changes::class.java).keys).containsExactlyInAnyOrder("brevo.apiKey", "mail.account.api")
            }
    }

    class Changes : ApplicationListener<EnvironmentChangeEvent> {
        val keys = mutableSetOf<String>()

        override fun onApplicationEvent(event: EnvironmentChangeEvent) {
            keys += event.keys
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(VaultKeyRefresher::class)
    class RefresherOnly {
        @Bean
        fun changes() = Changes()
    }

    companion object {
        private val vault = VaultDevServer()

        @JvmStatic
        @BeforeAll
        fun seed() {
            vault.start()
            vault.put("secret/api", "brevo.apiKey=first-brevo", "app.two-factor.key=two-factor")
            vault.put("secret/platform/mail", "account.api=first-smtp", "account.bounce=imap")
        }

        @JvmStatic
        @AfterAll
        fun stop() = vault.close()
    }
}
