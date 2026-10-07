package net.blueshell.api.email.domain

import net.blueshell.api.email.persistence.MailSecurity
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
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

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
        val ssl = senderFor(SmtpRoute("smtp.example.nl", 465, MailSecurity.SSL, SmtpLogin("events", "secret")))
        assertThat(listOf(ssl.host, ssl.port, ssl.username, ssl.password))
            .containsExactly("smtp.example.nl", 465, "events", "secret")
        assertThat(ssl.javaMailProperties["mail.smtp.ssl.enable"]).isEqualTo("true")
        assertThat(ssl.javaMailProperties["mail.smtp.ssl.checkserveridentity"]).isEqualTo("true")
        val starttls = senderFor(SmtpRoute("smtp.example.nl", 587, MailSecurity.STARTTLS, SmtpLogin("events", "secret")))
        assertThat(starttls.javaMailProperties["mail.smtp.starttls.required"]).isEqualTo("true")
        assertThat(starttls.javaMailProperties["mail.smtp.ssl.enable"]).isEqualTo("false")

        val imaps = sessionFor(ImapRoute("imap.example.nl", 993, MailSecurity.SSL, SmtpLogin("events", "secret"))).properties
        assertThat(listOf(imaps["mail.imap.ssl.enable"], imaps["mail.imap.ssl.checkserveridentity"], imaps["mail.imap.starttls.required"]))
            .containsExactly("true", "true", "false")

        // Nothing listens on port 1, so the probe reports the refusal rather than keeping the login.
        assertThatThrownBy { JavaMailProbe().sends(SmtpRoute("127.0.0.1", 1, MailSecurity.NONE, SmtpLogin("events", "secret"))) }
            .isInstanceOfSatisfying(MailLoginRefused::class.java) { assertThat(it.facts["protocol"]).isEqualTo("SMTP") }
        assertThatThrownBy { JavaMailProbe().reads(ImapRoute("127.0.0.1", 1, MailSecurity.NONE, SmtpLogin("events", "secret"))) }
            .isInstanceOfSatisfying(MailLoginRefused::class.java) { assertThat(it.facts["protocol"]).isEqualTo("IMAP") }
    }

    @Test
    fun `keeps a login the servers accept`() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val answering = thread { server.accept().use(::answerSmtp) }

            JavaMailProbe().sends(SmtpRoute("127.0.0.1", server.localPort, MailSecurity.NONE, SmtpLogin("events", "secret")))

            answering.join(TIMEOUT_MS)
        }
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val answering = thread { server.accept().use(::answerImap) }

            JavaMailProbe().reads(ImapRoute("127.0.0.1", server.localPort, MailSecurity.NONE, SmtpLogin("events", "secret")))

            answering.join(TIMEOUT_MS)
        }
    }

    /** Just enough of an IMAP server to accept one login: a greeting, its capabilities, the login and LOGOUT. */
    private fun answerImap(socket: Socket) {
        val reader = socket.getInputStream().bufferedReader()
        val writer = socket.getOutputStream().bufferedWriter()

        fun say(line: String) = writer.apply { write("$line\r\n") }.flush()
        say("* OK test IMAP4rev1 ready")
        while (true) {
            val line = reader.readLine() ?: return
            val tag = line.substringBefore(" ")
            when {
                line.contains("CAPABILITY") -> say("* CAPABILITY IMAP4rev1 AUTH=PLAIN\r\n$tag OK done")
                line.contains("AUTHENTICATE") -> {
                    say("+ ")
                    reader.readLine()
                    say("$tag OK logged in")
                }
                line.contains("LOGOUT") -> return say("* BYE\r\n$tag OK bye")
                else -> say("$tag OK done")
            }
        }
    }

    /** Just enough of an SMTP server to accept one login: a greeting, EHLO with AUTH PLAIN, the login and QUIT. */
    private fun answerSmtp(socket: Socket) {
        val reader = socket.getInputStream().bufferedReader()
        val writer = socket.getOutputStream().bufferedWriter()

        fun say(line: String) = writer.apply { write("$line\r\n") }.flush()
        say("220 test ESMTP")
        while (true) {
            val line = reader.readLine() ?: return
            when {
                line.startsWith("EHLO") -> say("250-test\r\n250 AUTH PLAIN")
                line.startsWith("AUTH") -> say("235 2.7.0 Accepted")
                line.startsWith("QUIT") -> return say("221 Bye")
                else -> say("250 OK")
            }
        }
    }

    @Test
    fun `sends a login unencrypted only to a server on this network`() {
        val login = SmtpLogin("events", "secret")
        assertThat(listOf("127.0.0.1", "10.0.0.5", "192.168.1.2", "fe80::1").map(::onThisNetwork)).containsOnly(true)
        assertThat(listOf("93.184.216.34", "2606:4700::1111", "no-such-host.invalid").map(::onThisNetwork)).containsOnly(false)

        assertThatThrownBy { JavaMailProbe().sends(SmtpRoute("93.184.216.34", 25, MailSecurity.NONE, login)) }
            .isInstanceOfSatisfying(MailNeedsEncryption::class.java) { assertThat(it.facts["host"]).isEqualTo("93.184.216.34") }
        assertThatThrownBy { JavaMailProbe().reads(ImapRoute("93.184.216.34", 143, MailSecurity.NONE, login)) }
            .isInstanceOfSatisfying(MailNeedsEncryption::class.java) { assertThat(it.facts["protocol"]).isEqualTo("IMAP") }
    }

    private companion object {
        const val TIMEOUT_MS = 5000L
    }
}
