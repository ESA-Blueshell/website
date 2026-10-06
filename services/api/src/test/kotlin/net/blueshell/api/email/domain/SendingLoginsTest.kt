package net.blueshell.api.email.domain

import net.blueshell.api.email.persistence.SmtpSecurity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.vault.VaultException
import org.springframework.vault.core.VaultTemplate
import org.springframework.vault.support.VaultResponse

class SendingLoginsTest {
    private val vault: VaultTemplate = mock()
    private val logins = VaultSendingLogins(vault)

    @Test
    fun `keeps each login at its own path in Vault, reads it back, and removes every version`() {
        logins.write(7, SmtpLogin("events", "secret"))
        verify(vault).write("secret/data/api/sending/7", mapOf("data" to mapOf("username" to "events", "password" to "secret")))

        val kept = VaultResponse().apply { data = mapOf("data" to mapOf("username" to "events", "password" to "secret")) }
        whenever(vault.read("secret/data/api/sending/7")).thenReturn(kept)
        assertThat(logins.read(7)).isEqualTo(SmtpLogin("events", "secret"))
        assertThat(logins.read(8)).isNull()

        logins.delete(7)
        verify(vault).delete("secret/metadata/api/sending/7")
    }

    @Test
    fun `says the store cannot be reached when Vault does not answer`() {
        whenever(vault.read("secret/data/api/sending/7")).doThrow(VaultException("sealed"))
        assertThatThrownBy { logins.read(7) }.isInstanceOf(SendingLoginsUnavailable::class.java)
    }

    @Test
    fun `a route's sender carries its server, its login and its security, and a refused connection says so`() {
        val ssl = senderFor(SmtpRoute("smtp.example.nl", 465, SmtpSecurity.SSL, SmtpLogin("events", "secret")))
        assertThat(listOf(ssl.host, ssl.port, ssl.username, ssl.password))
            .containsExactly("smtp.example.nl", 465, "events", "secret")
        assertThat(ssl.javaMailProperties["mail.smtp.ssl.enable"]).isEqualTo("true")
        assertThat(ssl.javaMailProperties["mail.smtp.ssl.checkserveridentity"]).isEqualTo("true")
        val starttls = senderFor(SmtpRoute("smtp.example.nl", 587, SmtpSecurity.STARTTLS, SmtpLogin("events", "secret")))
        assertThat(starttls.javaMailProperties["mail.smtp.starttls.required"]).isEqualTo("true")
        assertThat(starttls.javaMailProperties["mail.smtp.ssl.enable"]).isEqualTo("false")

        // Nothing listens on port 1, so the probe reports the refusal rather than keeping the login.
        assertThatThrownBy { JavaMailSmtpProbe().test(SmtpRoute("127.0.0.1", 1, SmtpSecurity.NONE, SmtpLogin("events", "secret"))) }
            .isInstanceOf(SmtpLoginRefused::class.java)
    }

    @Test
    fun `sends a login unencrypted only to a server on this network`() {
        val login = SmtpLogin("events", "secret")
        assertThat(listOf("127.0.0.1", "10.0.0.5", "192.168.1.2", "fe80::1").map(::onThisNetwork)).containsOnly(true)
        assertThat(listOf("93.184.216.34", "2606:4700::1111", "no-such-host.invalid").map(::onThisNetwork)).containsOnly(false)

        assertThatThrownBy { JavaMailSmtpProbe().test(SmtpRoute("93.184.216.34", 25, SmtpSecurity.NONE, login)) }
            .isInstanceOfSatisfying(SmtpNeedsEncryption::class.java) { assertThat(it.facts["host"]).isEqualTo("93.184.216.34") }
    }
}
