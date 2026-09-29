package net.blueshell.api.email.domain

import jakarta.mail.Provider
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.URLName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.mock.env.MockEnvironment
import java.util.Properties

class ImapBouncePollingServiceTest {
    private val emailService: EmailService = mock()
    private val environment = MockEnvironment().withProperty("email.bounce.imap.password", "first")
    private val poller =
        ImapBouncePollingService(emailService, "mail.test", 993, "bounce@example.com", environment, "INBOX", false).apply {
            sessionFor = { protocol ->
                Session.getInstance(Properties()).apply {
                    setProvider(Provider(Provider.Type.STORE, protocol, RecordingStore::class.java.name, "test", "1"))
                }
            }
        }

    /** Records the password each connect offers and has no folders, so a poll stops there. */
    class RecordingStore(
        session: Session,
        url: URLName?,
    ) : Store(session, url) {
        override fun protocolConnect(
            host: String?,
            port: Int,
            user: String?,
            password: String?,
        ): Boolean {
            logins += password.orEmpty()
            return true
        }

        override fun getDefaultFolder() = throw UnsupportedOperationException()

        override fun getFolder(name: String?) = throw UnsupportedOperationException()

        override fun getFolder(url: URLName?) = throw UnsupportedOperationException()

        companion object {
            val logins = mutableListOf<String>()
        }
    }

    @Test
    fun `a poll connects over the protocol the TLS switch names`() {
        val real = ImapBouncePollingService(emailService, "mail.test", 993, "bounce@example.com", environment, "INBOX", true)

        assertThat(real.sessionFor("imaps").getProperty("mail.store.protocol")).isEqualTo("imaps")
    }

    @Test
    fun `each poll logs in with the password as it stands, so a rotated one is used next`() {
        RecordingStore.logins.clear()

        poller.pollBounces()
        environment.setProperty("email.bounce.imap.password", "second")
        poller.pollBounces()

        assertThat(RecordingStore.logins).containsExactly("first", "second")
        verifyNoInteractions(emailService)
    }
}
