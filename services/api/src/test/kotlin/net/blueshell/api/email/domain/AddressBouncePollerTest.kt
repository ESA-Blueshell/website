package net.blueshell.api.email.domain

import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.search.SearchTerm
import net.blueshell.api.email.api.AddressMailboxes
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AddressBouncePollerTest {
    @Test
    fun `takes the unseen messages in each address's mailbox, opened for writing so each is marked seen`() {
        val message = mock<Message>()
        val folder = mock<Folder>()
        whenever(folder.search(any<SearchTerm>())).thenReturn(arrayOf(message))
        var mode = -1
        val mailboxes =
            AddressMailboxes { opened, visit ->
                mode = opened
                visit("events@b.nl", folder)
            }

        AddressBouncePoller(mailboxes, mock()).poll()

        assert(mode == Folder.READ_WRITE)
        // Not a delivery report: it is only marked seen.
        verify(message).setFlag(Flags.Flag.SEEN, true)
    }
}
