package net.blueshell.api.email.domain

import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.search.SearchTerm
import net.blueshell.api.email.api.AddressMailboxes
import net.blueshell.api.email.persistence.Email
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AddressBouncePollerTest {
    private val report = mock<Message>()
    private val reply = mock<Message>()
    private val folder = mock<Folder>()
    private val emails = mock<EmailService>()
    private var mode = -1
    private val mailboxes =
        AddressMailboxes { opened, visit ->
            mode = opened
            visit("events@b.nl", folder)
        }

    private fun bounceOf(id: String) = BounceMessageParser.ParsedBounce(id, null, null, null, "550")

    @Test
    fun `reads each address's mailbox without marking anything, so the committee's own mail stays unread`() {
        whenever(folder.search(any<SearchTerm>())).thenReturn(arrayOf(report, reply))

        val poller = AddressBouncePoller(mailboxes, emails)
        poller.parse = { message -> bounceOf("<sent-1>").takeIf { message === report } }
        poller.poll()

        assertThat(mode).isEqualTo(Folder.READ_ONLY)
        verify(report, never()).setFlag(any(), any())
        verify(reply, never()).setFlag(any(), any())
        verify(emails).findByMessageId("<sent-1>")
    }

    @Test
    fun `marks an email bounced once, however often its report is read again`() {
        val sent =
            Email(recipientEmail = "a@b.nl").apply {
                deliveryStatus = EmailDeliveryStatus.SENT
                senderAddress = "Events@b.nl"
            }
        val bounced =
            Email(recipientEmail = "c@d.nl").apply {
                deliveryStatus = EmailDeliveryStatus.BOUNCED
                senderAddress = "events@b.nl"
            }
        whenever(folder.search(any<SearchTerm>())).thenReturn(arrayOf(report, reply))
        whenever(emails.findByMessageId("<sent-1>")).thenReturn(sent)
        whenever(emails.findByMessageId("<sent-2>")).thenReturn(bounced)
        val poller = AddressBouncePoller(mailboxes, emails)
        poller.parse = { message -> bounceOf(if (message === report) "<sent-1>" else "<sent-2>") }

        poller.poll()

        verify(emails).markBounced(sent, "diagnostic=550")

        // A message that breaks the reading is passed, and the reading goes on.
        poller.parse = { message -> if (message === report) error("unreadable") else bounceOf("<sent-1>") }
        poller.poll()
        verify(emails, org.mockito.kotlin.times(2)).findByMessageId("<sent-1>")
        verify(emails, never()).markBounced(org.mockito.kotlin.eq(bounced), anyOrNull())
    }

    @Test
    fun `takes a report only for an email that went out from the mailbox it landed in`() {
        // Anyone can mail a report to the address; one naming another address's email is not this mailbox's to take.
        val fromBoard =
            Email(recipientEmail = "a@b.nl").apply {
                deliveryStatus = EmailDeliveryStatus.SENT
                senderAddress = "board@b.nl"
            }
        whenever(folder.search(any<SearchTerm>())).thenReturn(arrayOf(report))
        whenever(emails.findByMessageId("<sent-3>")).thenReturn(fromBoard)
        val poller = AddressBouncePoller(mailboxes, emails)
        poller.parse = { bounceOf("<sent-3>") }

        poller.poll()

        verify(emails, never()).markBounced(org.mockito.kotlin.eq(fromBoard), anyOrNull())
    }
}
