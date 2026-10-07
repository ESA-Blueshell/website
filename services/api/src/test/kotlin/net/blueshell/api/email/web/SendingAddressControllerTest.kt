package net.blueshell.api.email.web

import net.blueshell.api.email.domain.SendingAddressChange
import net.blueshell.api.email.domain.SendingAddressView
import net.blueshell.api.email.domain.SendingAddresses
import net.blueshell.api.email.persistence.MailSecurity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SendingAddressControllerTest {
    private val addresses: SendingAddresses = mock()
    private val controller = SendingAddressController(addresses)
    private val view =
        SendingAddressView(
            id = 7,
            address = "events@esa-blueshell.nl",
            displayName = "Events",
            host = "smtp",
            port = 587,
            security = MailSecurity.STARTTLS,
            imapHost = "imap",
            imapPort = 993,
            imapSecurity = MailSecurity.SSL,
            isDefault = false,
            loginKept = true,
            canSend = true,
            canRead = true,
            sendFailure = null,
            readFailure = null,
            checkedAt = null,
        )
    private val request =
        SendingAddressRequest(
            "events@esa-blueshell.nl",
            "Events",
            "smtp",
            587,
            MailSecurity.STARTTLS,
            username = "events",
            password = "secret",
            imapHost = "imap",
            imapPort = 993,
            imapSecurity = MailSecurity.SSL,
        )
    private val change =
        SendingAddressChange(
            address = "events@esa-blueshell.nl",
            displayName = "Events",
            host = "smtp",
            port = 587,
            security = MailSecurity.STARTTLS,
            isDefault = false,
            username = "events",
            password = "secret",
            imapHost = "imap",
            imapPort = 993,
            imapSecurity = MailSecurity.SSL,
        )

    @Test
    fun `lists, adds, changes, checks and removes sending addresses`() {
        whenever(addresses.list()).thenReturn(listOf(view))
        whenever(addresses.check(7)).thenReturn(view)
        whenever(addresses.add(change)).thenReturn(view)
        whenever(addresses.update(7, change)).thenReturn(view)

        assertThat(controller.listSendingAddresses()).containsExactly(view)
        assertThat(controller.addSendingAddress(request)).isEqualTo(view)
        assertThat(controller.setSendingAddress(7, request)).isEqualTo(view)
        assertThat(controller.checkSendingAddress(7)).isEqualTo(view)
        controller.removeSendingAddress(7)
        verify(addresses).remove(7)
    }
}
