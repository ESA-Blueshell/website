package net.blueshell.api.email.domain

import jakarta.mail.Folder
import jakarta.mail.Store
import net.blueshell.api.email.persistence.MailSecurity
import net.blueshell.api.email.persistence.SendingAddress
import net.blueshell.api.email.persistence.SendingAddressRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ImapAddressMailboxesTest {
    private fun address(
        id: Long,
        address: String,
        imapHost: String?,
    ) = SendingAddress(
        address,
        "A",
        "smtp",
        587,
        MailSecurity.STARTTLS,
        imapHost = imapHost,
        imapPort = 993,
        imapSecurity = MailSecurity.SSL,
    ).also { it.id = id }

    @Test
    fun `opens each read address's mailbox with its kept login, and one that cannot be opened leaves the others`() {
        val repository =
            mock<SendingAddressRepository> {
                on { findAll() } doReturn
                    listOf(address(1, "events@b.nl", "imap.b.nl"), address(2, "board@b.nl", null), address(3, "gone@b.nl", "imap.b.nl"))
            }
        val logins = LocalSendingLogins().apply { write(1, SmtpLogin("events", "secret")) }
        val folder = mock<Folder>()
        val store = mock<Store> { on { getFolder("INBOX") } doReturn folder }
        whenever(store.isConnected).thenReturn(true)
        whenever(folder.isOpen).thenReturn(true)
        val routes = mutableListOf<ImapRoute>()
        val mailboxes = ImapAddressMailboxes(repository, logins).apply { storeFor = { route -> store.also { routes += route } } }
        val visited = mutableListOf<String>()

        mailboxes.eachOpen(Folder.READ_WRITE) { address, opened -> visited += address.also { assertThat(opened).isSameAs(folder) } }

        assertThat(visited).containsExactly("events@b.nl")
        assertThat(routes).containsExactly(ImapRoute("imap.b.nl", 993, MailSecurity.SSL, SmtpLogin("events", "secret")))
        verify(folder).open(Folder.READ_WRITE)
        verify(folder).close(false)
        verify(store).close()
    }

    @Test
    fun `its real store logs in over the route's session`() {
        val mailboxes = ImapAddressMailboxes(mock(), LocalSendingLogins())
        val route = ImapRoute("127.0.0.1", 1, MailSecurity.NONE, SmtpLogin("events", "secret"))

        assertThat(runCatching { mailboxes.storeFor(route) }.exceptionOrNull()).isNotNull
    }
}
