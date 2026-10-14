package net.blueshell.api.mail.domain

import jakarta.mail.Folder
import net.blueshell.api.email.api.AddressMailboxes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class AddressInboxPollerTest {
    @Test
    fun `reads each address's mailbox read-only, keeping its place under the address`() {
        val folder = mock<Folder>()
        val reading = mock<MailboxReading>()
        var mode = -1
        val mailboxes =
            AddressMailboxes { opened, visit ->
                mode = opened
                visit("Events@b.nl", folder)
            }

        AddressInboxPoller(mailboxes, reading).poll()

        assertThat(mode).isEqualTo(Folder.READ_ONLY)
        verify(reading).read(folder, "address:events@b.nl", "Events@b.nl")
    }
}
