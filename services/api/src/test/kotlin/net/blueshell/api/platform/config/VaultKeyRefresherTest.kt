package net.blueshell.api.platform.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cloud.context.environment.EnvironmentChangeEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.vault.VaultException
import org.springframework.vault.core.VaultKeyValueOperations
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend
import org.springframework.vault.core.VaultTemplate
import org.springframework.vault.support.VaultResponse

class VaultKeyRefresherTest {
    private val api: VaultKeyValueOperations = mock()
    private val vault: VaultTemplate =
        mock { on { opsForKeyValue(eq("secret"), eq(KeyValueBackend.KV_2)) } doReturn api }
    private val events: ApplicationEventPublisher = mock()
    private val environment =
        StandardEnvironment().apply {
            propertySources.addLast(
                MapPropertySource(
                    "imported",
                    mapOf(
                        "spring.config.import[0]" to "vault://secret/api",
                        "spring.config.import[1]" to "vault://secret/platform/mail?prefix=mail.",
                        "spring.config.import[2]" to "optional:vault://",
                        "brevo.apiKey" to "old-brevo",
                        "discord.botToken" to "old-discord",
                        "mail.account.api" to "old-smtp",
                        "spring.mail.password" to "\${mail.account.api:}",
                    ),
                ),
            )
        }

    private fun vaultHolds(
        api: Map<String, Any>,
        mail: Map<String, Any> = mapOf("account.api" to "old-smtp"),
    ) {
        whenever(this.api.get("api")).doReturn(VaultResponse().apply { data = api })
        whenever(this.api.get("platform/mail")).doReturn(VaultResponse().apply { data = mail })
    }

    private fun refresher() = VaultKeyRefresher(vault, environment, events)

    private fun changedKeys(): Set<String> {
        val event = argumentCaptor<EnvironmentChangeEvent>()
        verify(events).publishEvent(event.capture())
        return event.firstValue.keys
    }

    @Test
    fun `a key changed in Vault replaces the one in use and is named in one change event`() {
        vaultHolds(mapOf("brevo.apiKey" to "old-brevo", "discord.botToken" to "old-discord"))
        val refresher = refresher()

        vaultHolds(mapOf("brevo.apiKey" to "new-brevo", "discord.botToken" to "old-discord"))
        refresher.refresh()

        assertThat(environment.getProperty("brevo.apiKey")).isEqualTo("new-brevo")
        assertThat(changedKeys()).containsExactly("brevo.apiKey")
    }

    @Test
    fun `a mail password changes under the prefix, and the property mapped from it follows`() {
        vaultHolds(mapOf("brevo.apiKey" to "old-brevo"))
        val refresher = refresher()

        vaultHolds(mapOf("brevo.apiKey" to "old-brevo"), mapOf("account.api" to "new-smtp"))
        refresher.refresh()

        assertThat(environment.getProperty("spring.mail.password")).isEqualTo("new-smtp")
        assertThat(changedKeys()).containsExactly("mail.account.api")
    }

    @Test
    fun `a key Vault no longer holds is blank, as on a fresh start`() {
        vaultHolds(mapOf("brevo.apiKey" to "old-brevo", "discord.botToken" to "old-discord"))
        val refresher = refresher()

        vaultHolds(mapOf("brevo.apiKey" to "old-brevo"))
        refresher.refresh()

        assertThat(environment.getProperty("discord.botToken")).isEmpty()
        assertThat(changedKeys()).containsExactly("discord.botToken")
    }

    @Test
    fun `nothing changed means no change event`() {
        vaultHolds(mapOf("brevo.apiKey" to "old-brevo", "discord.botToken" to "old-discord"))
        refresher().refresh()

        verify(events, never()).publishEvent(any<Any>())
    }

    @Test
    fun `a read Vault refuses keeps every secret in use`() {
        vaultHolds(mapOf("brevo.apiKey" to "old-brevo"))
        val refresher = refresher()

        whenever(api.get("api")).doThrow(VaultException("Status 403 Forbidden"))
        refresher.refresh()

        assertThat(environment.getProperty("brevo.apiKey")).isEqualTo("old-brevo")
        verify(events, never()).publishEvent(any<Any>())
        verify(api, times(2)).get("api")
    }
}
