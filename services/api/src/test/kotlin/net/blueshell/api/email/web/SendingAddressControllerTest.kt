package net.blueshell.api.email.web

import net.blueshell.api.email.domain.SendingAddressChange
import net.blueshell.api.email.domain.SendingAddressView
import net.blueshell.api.email.domain.SendingAddresses
import net.blueshell.api.email.persistence.MailSecurity
import net.blueshell.api.security.AdminOnly
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.reflect.full.findAnnotation

class SendingAddressControllerTest {
    private val addresses: SendingAddresses = mock()
    private val currentUser: CurrentUserProvider = mock()
    private val controller = SendingAddressController(addresses, currentUser)
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

    @Test
    fun `never writes a password out when a request is printed`() {
        assertThat(request.toString()).doesNotContain("secret").contains("password=***")
    }

    @Test
    fun `holds a board member to what the board may change, lets an admin past, and leaves removal to an admin`() {
        whenever(addresses.add(change)).thenReturn(view)
        whenever(currentUser.currentUser()).thenReturn(CurrentUser(id = 6, roles = setOf(Role.BOARD), addressId = null))
        controller.addSendingAddress(request)
        verify(addresses).boardMayMake(null, change)

        whenever(currentUser.currentUser()).thenReturn(CurrentUser(id = 1, roles = setOf(Role.ADMIN), addressId = null))
        controller.setSendingAddress(7, request)
        verify(addresses, never()).boardMayMake(7, change)
        assertThat(SendingAddressController::removeSendingAddress.findAnnotation<AdminOnly>()).isNotNull
    }
}
