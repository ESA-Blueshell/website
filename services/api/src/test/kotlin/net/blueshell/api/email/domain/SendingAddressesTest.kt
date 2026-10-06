package net.blueshell.api.email.domain

import net.blueshell.api.email.persistence.SendingAddress
import net.blueshell.api.email.persistence.SendingAddressRepository
import net.blueshell.api.email.persistence.SmtpSecurity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class SendingAddressesTest {
    private val repository: SendingAddressRepository =
        mock {
            on { save(any<SendingAddress>()) } doAnswer {
                (it.arguments[0] as SendingAddress).also { row -> if (row.id == null) row.id = 7 }
            }
        }
    private val logins = LocalSendingLogins()
    private val probe: SmtpProbe = mock()
    private val now = Instant.parse("2026-10-06T10:00:00Z")
    private val addresses = SendingAddresses(repository, logins, probe, Clock.fixed(now, ZoneOffset.UTC))

    private fun change(
        address: String = "events@esa-blueshell.nl",
        host: String = "smtp.example.nl",
        port: Int = 587,
        isDefault: Boolean = false,
        username: String? = "events",
        password: String? = "secret",
    ) = SendingAddressChange(address, " Events ", host, port, SmtpSecurity.STARTTLS, isDefault, username, password)

    @Test
    fun `adds an address once its login works, keeping the login apart and never answering it`() {
        val other =
            SendingAddress("board@esa-blueshell.nl", "Board", "smtp", 587, SmtpSecurity.STARTTLS, isDefault = true).also { it.id = 3 }
        whenever(repository.findAll()).thenReturn(listOf(other))

        val view = addresses.add(change(isDefault = true))

        verify(probe).test(SmtpRoute("smtp.example.nl", 587, SmtpSecurity.STARTTLS, SmtpLogin("events", "secret")))
        assertThat(view)
            .isEqualTo(
                SendingAddressView(7, "events@esa-blueshell.nl", "Events", "smtp.example.nl", 587, SmtpSecurity.STARTTLS, true, true),
            )
        assertThat(logins.read(7)).isEqualTo(SmtpLogin("events", "secret"))
        assertThat(other.isDefault).isFalse()
        assertThat(SmtpLogin("events", "secret").toString()).doesNotContain("secret")
    }

    @Test
    fun `refuses an address taken, one without a login, and keeps nothing when the server refuses the login`() {
        whenever(repository.existsByAddressIgnoreCase("events@esa-blueshell.nl")).thenReturn(true)
        assertThatThrownBy { addresses.add(change()) }.isInstanceOf(SendingAddressTaken::class.java)

        whenever(repository.existsByAddressIgnoreCase("events@esa-blueshell.nl")).thenReturn(false)
        assertThatThrownBy { addresses.add(change(password = " ".trim())) }.isInstanceOf(SendingAddressNeedsLogin::class.java)

        whenever(probe.test(any())).doThrow(SmtpLoginRefused("535 Authentication failed"))
        assertThatThrownBy { addresses.add(change()) }
            .isInstanceOfSatisfying(SmtpLoginRefused::class.java) { assertThat(it.facts["reason"]).isEqualTo("535 Authentication failed") }
        verify(repository, never()).save(any<SendingAddress>())
        assertThat(logins.read(7)).isNull()
    }

    @Test
    fun `changes an address, trying only a login given with the change`() {
        val kept =
            SendingAddress("events@esa-blueshell.nl", "Events", "smtp.example.nl", 587, SmtpSecurity.STARTTLS, isDefault = true)
                .also { it.id = 7 }
        whenever(repository.findById(7)).thenReturn(Optional.of(kept))
        logins.write(7, SmtpLogin("events", "old"))

        addresses.update(7, change(username = null, password = null))
        verify(probe, never()).test(any())
        assertThat(kept.isDefault).isFalse()

        addresses.update(7, change(host = "mail.example.nl", port = 465, password = "new"))
        verify(probe).test(SmtpRoute("mail.example.nl", 465, SmtpSecurity.STARTTLS, SmtpLogin("events", "new")))
        assertThat(logins.read(7)).isEqualTo(SmtpLogin("events", "new"))
        assertThat(kept.loginKeptAt).isEqualTo(now)

        whenever(repository.existsByAddressIgnoreCase("board@esa-blueshell.nl")).thenReturn(true)
        assertThatThrownBy { addresses.update(7, change(address = "board@esa-blueshell.nl")) }.isInstanceOf(SendingAddressTaken::class.java)
        assertThatThrownBy { addresses.update(8, change()) }.isInstanceOf(SendingAddressNotFound::class.java)
    }

    @Test
    fun `never sends a kept login to a server it was not tried on`() {
        val kept =
            SendingAddress("events@esa-blueshell.nl", "Events", "smtp.example.nl", 587, SmtpSecurity.STARTTLS).also { it.id = 7 }
        whenever(repository.findById(7)).thenReturn(Optional.of(kept))
        logins.write(7, SmtpLogin("events", "old"))

        assertThatThrownBy { addresses.update(7, change(host = "attacker.example", username = null, password = null)) }
            .isInstanceOf(SendingAddressNeedsLogin::class.java)
        assertThatThrownBy { addresses.update(7, change(port = 2525, username = null, password = null)) }
            .isInstanceOf(SendingAddressNeedsLogin::class.java)
        val plain = SendingAddressChange("events@esa-blueshell.nl", "Events", "smtp.example.nl", 587, SmtpSecurity.NONE, false, null, null)
        assertThatThrownBy { addresses.update(7, plain) }.isInstanceOf(SendingAddressNeedsLogin::class.java)

        verify(probe, never()).test(any())
        assertThat(listOf(kept.host, kept.port, kept.security)).containsExactly("smtp.example.nl", 587, SmtpSecurity.STARTTLS)
        assertThat(logins.read(7)).isEqualTo(SmtpLogin("events", "old"))
    }

    @Test
    fun `routes an email by the address and its login, removes both, and lists without logins`() {
        val kept = SendingAddress("events@esa-blueshell.nl", "Events", "smtp.example.nl", 465, SmtpSecurity.SSL).also { it.id = 7 }
        whenever(repository.findById(7)).thenReturn(Optional.of(kept))
        whenever(repository.findAllByOrderByAddress()).thenReturn(listOf(kept))
        whenever(repository.existsById(7)).thenReturn(true)
        logins.write(7, SmtpLogin("events", "secret"))

        assertThat(addresses.routeFor(7)).isEqualTo(
            SendingRoute(
                "events@esa-blueshell.nl",
                "Events",
                SmtpRoute("smtp.example.nl", 465, SmtpSecurity.SSL, SmtpLogin("events", "secret")),
            ),
        )
        assertThat(addresses.list().single().loginKept).isFalse()
        assertThat(addresses.exists(7)).isTrue()

        addresses.remove(7)
        verify(repository).delete(kept)
        assertThat(logins.read(7)).isNull()
        assertThatThrownBy { addresses.routeFor(7) }.isInstanceOf(SendingAddressNeedsLogin::class.java)
    }
}
