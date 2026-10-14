package net.blueshell.api.email.domain

import net.blueshell.api.email.persistence.MailSecurity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class MailServersTest {
    private val servers = MailServers(listOf(25, 465, 587, 143, 993), listOf("mail.internal.example"))

    @Test
    fun `lets the site reach a mail port on a server on the internet`() {
        servers.allow(MailProtocol.SMTP, "93.184.216.34", 587, MailSecurity.STARTTLS)
        servers.allow(MailProtocol.IMAP, "93.184.216.34", 993, MailSecurity.SSL)
    }

    @Test
    fun `refuses any other port, so no other service can be reached by an address`() {
        assertThatThrownBy { servers.allow(MailProtocol.SMTP, "93.184.216.34", 8200, MailSecurity.SSL) }
            .isInstanceOfSatisfying(MailServerNotAllowed::class.java) {
                assertThat(it.facts).containsEntry("host", "93.184.216.34").containsEntry("port", 8200)
            }
    }

    @Test
    fun `refuses a server on the site's own network unless it is named as one of the site's own`() {
        val inside =
            listOf("127.0.0.1", "10.0.0.5", "192.168.1.2", "169.254.169.254", "::1", "fd00::1", "0.0.0.0", "100.64.1.1", "198.18.0.1")
        for (host in inside) {
            assertThatThrownBy { servers.allow(MailProtocol.IMAP, host, 993, MailSecurity.SSL) }
                .isInstanceOf(MailServerNotAllowed::class.java)
        }
        MailServers(listOf(993), listOf("10.0.0.5")).allow(MailProtocol.IMAP, "10.0.0.5", 993, MailSecurity.NONE)
        MailServers(listOf(993), listOf("Mail.Internal.Example")).allow(MailProtocol.IMAP, "mail.internal.example", 993, MailSecurity.SSL)
    }

    @Test
    fun `says when a server's name does not resolve, rather than calling it inside the network`() {
        assertThatThrownBy { servers.allow(MailProtocol.SMTP, "no.such.host.invalid", 587, MailSecurity.SSL) }
            .isInstanceOfSatisfying(MailServerUnknown::class.java) { assertThat(it.facts).containsEntry("host", "no.such.host.invalid") }
    }

    @Test
    fun `sends a login unencrypted only to a server on the site's own network, each time it connects`() {
        assertThatThrownBy { servers.allow(MailProtocol.SMTP, "93.184.216.34", 587, MailSecurity.NONE) }
            .isInstanceOf(MailNeedsEncryption::class.java)
        // Named as one of the site's own, but it resolves outside: still no plain login.
        val named = MailServers(listOf(587), listOf("93.184.216.34"))
        assertThatThrownBy { named.allow(MailProtocol.SMTP, "93.184.216.34", 587, MailSecurity.NONE) }
            .isInstanceOf(MailNeedsEncryption::class.java)
    }
}
