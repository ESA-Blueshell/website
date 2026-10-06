package net.blueshell.api.email.web

import net.blueshell.api.email.domain.SendingAddressChange
import net.blueshell.api.email.domain.SendingAddressView
import net.blueshell.api.email.domain.SendingAddresses
import net.blueshell.api.email.persistence.SmtpSecurity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SendingAddressControllerTest {
    private val addresses: SendingAddresses = mock()
    private val controller = SendingAddressController(addresses)
    private val view = SendingAddressView(7, "events@esa-blueshell.nl", "Events", "smtp", 587, SmtpSecurity.STARTTLS, false, true)
    private val request =
        SendingAddressRequest(
            "events@esa-blueshell.nl",
            "Events",
            "smtp",
            587,
            SmtpSecurity.STARTTLS,
            username = "events",
            password = "secret",
        )
    private val change =
        SendingAddressChange("events@esa-blueshell.nl", "Events", "smtp", 587, SmtpSecurity.STARTTLS, false, "events", "secret")

    @Test
    fun `lists, adds, changes and removes sending addresses`() {
        whenever(addresses.list()).thenReturn(listOf(view))
        whenever(addresses.add(change)).thenReturn(view)
        whenever(addresses.update(7, change)).thenReturn(view)

        assertThat(controller.listSendingAddresses()).containsExactly(view)
        assertThat(controller.addSendingAddress(request)).isEqualTo(view)
        assertThat(controller.setSendingAddress(7, request)).isEqualTo(view)
        controller.removeSendingAddress(7)
        verify(addresses).remove(7)
    }
}
