package net.blueshell.api.email.domain

import net.blueshell.api.email.persistence.MailSecurity
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AddressChecksTest {
    private fun view(id: Long) =
        SendingAddressView(
            id = id,
            address = "a$id@b.nl",
            displayName = "A",
            host = "smtp",
            port = 587,
            security = MailSecurity.STARTTLS,
            imapHost = null,
            imapPort = null,
            imapSecurity = null,
            isDefault = false,
            loginKept = true,
            canSend = null,
            canRead = null,
            sendFailure = null,
            readFailure = null,
            checkedAt = null,
        )

    @Test
    fun `checks every address, and one that cannot be checked leaves the others to be`() {
        val addresses = mock<SendingAddresses>()
        whenever(addresses.list()).thenReturn(listOf(view(1), view(2)))
        whenever(addresses.check(1)).doThrow(SendingAddressNotFound())

        AddressChecks(addresses).checkAll()

        verify(addresses).check(1)
        verify(addresses).check(2)
    }
}
