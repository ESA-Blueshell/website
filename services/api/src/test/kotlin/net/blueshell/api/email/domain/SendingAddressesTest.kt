package net.blueshell.api.email.domain

import net.blueshell.api.email.persistence.MailSecurity
import net.blueshell.api.email.persistence.SendingAddress
import net.blueshell.api.email.persistence.SendingAddressRepository
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
    private val probe: MailProbe = mock()
    private val now = Instant.parse("2026-10-06T10:00:00Z")
    private val addresses = SendingAddresses(repository, logins, probe, Clock.fixed(now, ZoneOffset.UTC))
    private val login = SmtpLogin("events", "secret")

    private fun change(
        address: String = "events@esa-blueshell.nl",
        host: String = "smtp.example.nl",
        port: Int = 587,
        isDefault: Boolean = false,
        username: String? = "events",
        password: String? = "secret",
        imapHost: String? = null,
        imapPort: Int? = 993,
        imapSecurity: MailSecurity? = MailSecurity.SSL,
    ) = SendingAddressChange(
        address,
        " Events ",
        host,
        port,
        MailSecurity.STARTTLS,
        isDefault,
        username,
        password,
        imapHost,
        imapPort,
        imapSecurity,
    )

    private fun kept(imapHost: String? = null) =
        SendingAddress("events@esa-blueshell.nl", "Events", "smtp.example.nl", 587, MailSecurity.STARTTLS, imapHost = imapHost).also {
            it.id = 7
            if (imapHost != null) {
                it.imapPort = 993
                it.imapSecurity = MailSecurity.SSL
            }
            whenever(repository.findById(7)).thenReturn(Optional.of(it))
        }

    @Test
    fun `adds an address once both its servers take the login, keeping the login apart and never answering it`() {
        val other =
            SendingAddress("board@esa-blueshell.nl", "Board", "smtp", 587, MailSecurity.STARTTLS, isDefault = true).also { it.id = 3 }
        whenever(repository.findAll()).thenReturn(listOf(other))

        val view = addresses.add(change(isDefault = true, imapHost = " imap.example.nl "))

        verify(probe).sends(SmtpRoute("smtp.example.nl", 587, MailSecurity.STARTTLS, login))
        verify(probe).reads(ImapRoute("imap.example.nl", 993, MailSecurity.SSL, login))
        assertThat(view).isEqualTo(
            SendingAddressView(
                id = 7,
                address = "events@esa-blueshell.nl",
                displayName = "Events",
                host = "smtp.example.nl",
                port = 587,
                security = MailSecurity.STARTTLS,
                imapHost = "imap.example.nl",
                imapPort = 993,
                imapSecurity = MailSecurity.SSL,
                isDefault = true,
                loginKept = true,
                canSend = true,
                canRead = true,
                sendFailure = null,
                readFailure = null,
                checkedAt = now,
            ),
        )
        assertThat(logins.read(7)).isEqualTo(login)
        assertThat(other.isDefault).isFalse()
        assertThat(login.toString()).doesNotContain("secret")
    }

    @Test
    fun `refuses an address taken, one without a login or with half an IMAP server, and keeps nothing a server refuses`() {
        whenever(repository.existsByAddressIgnoreCase("events@esa-blueshell.nl")).thenReturn(true)
        assertThatThrownBy { addresses.add(change()) }.isInstanceOf(SendingAddressTaken::class.java)

        whenever(repository.existsByAddressIgnoreCase("events@esa-blueshell.nl")).thenReturn(false)
        assertThatThrownBy { addresses.add(change(password = " ".trim())) }.isInstanceOf(SendingAddressNeedsLogin::class.java)
        assertThatThrownBy { addresses.add(change(imapHost = "imap", imapPort = null)) }.isInstanceOf(ImapServerIncomplete::class.java)
        assertThatThrownBy { addresses.add(change(imapHost = "imap", imapSecurity = null)) }.isInstanceOf(ImapServerIncomplete::class.java)

        whenever(probe.reads(any())).doThrow(MailLoginRefused(MailProtocol.IMAP, "NO [AUTHENTICATIONFAILED]"))
        assertThatThrownBy { addresses.add(change(imapHost = "imap.example.nl")) }
            .isInstanceOfSatisfying(MailLoginRefused::class.java) {
                assertThat(it.facts).containsEntry("protocol", "IMAP").containsEntry("reason", "NO [AUTHENTICATIONFAILED]")
            }
        verify(repository, never()).save(any<SendingAddress>())
        assertThat(logins.read(7)).isNull()
    }

    @Test
    fun `changes an address, trying only a login given with the change`() {
        val kept = kept().also { it.isDefault = true }
        logins.write(7, SmtpLogin("events", "old"))

        addresses.update(7, change(username = null, password = null))
        verify(probe, never()).sends(any())
        assertThat(kept.isDefault).isFalse()

        addresses.update(7, change(host = "mail.example.nl", port = 465, password = "new", imapHost = "imap.example.nl"))
        verify(probe).sends(SmtpRoute("mail.example.nl", 465, MailSecurity.STARTTLS, SmtpLogin("events", "new")))
        verify(probe).reads(ImapRoute("imap.example.nl", 993, MailSecurity.SSL, SmtpLogin("events", "new")))
        assertThat(logins.read(7)).isEqualTo(SmtpLogin("events", "new"))
        assertThat(listOf(kept.loginKeptAt, kept.checkedAt, kept.canRead)).containsExactly(now, now, true)

        whenever(repository.existsByAddressIgnoreCase("board@esa-blueshell.nl")).thenReturn(true)
        assertThatThrownBy { addresses.update(7, change(address = "board@esa-blueshell.nl")) }.isInstanceOf(SendingAddressTaken::class.java)
        assertThatThrownBy { addresses.update(8, change()) }.isInstanceOf(SendingAddressNotFound::class.java)
    }

    @Test
    fun `never sends a kept login to a server it was not tried on`() {
        val kept = kept(imapHost = "imap.example.nl")
        logins.write(7, SmtpLogin("events", "old"))
        val keeping = { host: String, port: Int ->
            change(host = host, port = port, username = null, password = null, imapHost = "imap.example.nl")
        }

        assertThatThrownBy { addresses.update(7, keeping("attacker.example", 587)) }.isInstanceOf(SendingAddressNeedsLogin::class.java)
        assertThatThrownBy { addresses.update(7, keeping("smtp.example.nl", 2525)) }.isInstanceOf(SendingAddressNeedsLogin::class.java)
        val plain = SendingAddressChange("events@esa-blueshell.nl", "Events", "smtp.example.nl", 587, MailSecurity.NONE, false, null, null)
        assertThatThrownBy { addresses.update(7, plain) }.isInstanceOf(SendingAddressNeedsLogin::class.java)
        // The IMAP server moving, or going, is the same: the login went to the old one.
        assertThatThrownBy { addresses.update(7, change(username = null, password = null, imapHost = "imap.attacker.example")) }
            .isInstanceOf(SendingAddressNeedsLogin::class.java)
        assertThatThrownBy { addresses.update(7, change(username = null, password = null)) }
            .isInstanceOf(SendingAddressNeedsLogin::class.java)

        verify(probe, never()).sends(any())
        verify(probe, never()).reads(any())
        assertThat(listOf(kept.host, kept.port, kept.security, kept.imapHost))
            .containsExactly("smtp.example.nl", 587, MailSecurity.STARTTLS, "imap.example.nl")
        assertThat(logins.read(7)).isEqualTo(SmtpLogin("events", "old"))
    }

    @Test
    fun `checks an address with the login kept and records what each server said`() {
        val kept = kept(imapHost = "imap.example.nl")
        logins.write(7, login)
        whenever(probe.reads(any())).doThrow(MailLoginRefused(MailProtocol.IMAP, "NO [AUTHENTICATIONFAILED]"))

        val view = addresses.check(7)

        verify(probe).sends(SmtpRoute("smtp.example.nl", 587, MailSecurity.STARTTLS, login))
        assertThat(listOf(view.canSend, view.sendFailure, view.canRead, view.readFailure, view.checkedAt))
            .containsExactly(true, null, false, "NO [AUTHENTICATIONFAILED]", now)
        assertThat(listOf(view.imapHost, view.imapPort, view.imapSecurity, kept.checkedAt))
            .containsExactly("imap.example.nl", 993, MailSecurity.SSL, now)
        // Hibernate builds a row through the no-arg constructor, which leaves an address unchecked.
        val built = SendingAddress::class.java.getDeclaredConstructor().newInstance()
        assertThat(built.checkedAt).isNull()

        kept.imapHost = null
        logins.delete(7)
        val unread = addresses.check(7)
        assertThat(listOf(unread.canSend, unread.sendFailure, unread.canRead, unread.readFailure))
            .containsExactly(false, "No login is kept.", null, null)
    }

    @Test
    fun `a check says Vault cannot be reached rather than refusing`() {
        kept()
        val down =
            object : SendingLogins {
                override fun write(
                    addressId: Long,
                    login: SmtpLogin,
                ) = Unit

                override fun read(addressId: Long): SmtpLogin? = throw SendingLoginsUnavailable()

                override fun delete(addressId: Long) = Unit
            }
        whenever(probe.sends(any())).doThrow(MailNeedsEncryption(MailProtocol.SMTP, "smtp.example.nl"))

        val view = SendingAddresses(repository, down, probe, Clock.fixed(now, ZoneOffset.UTC)).check(7)

        assertThat(view.sendFailure).isEqualTo("The store the logins are kept in cannot be reached now.")
        logins.write(7, login)
        assertThat(addresses.check(7).sendFailure).isEqualTo("A login only goes unencrypted to a server on the site's own network.")
    }

    @Test
    fun `routes an email by the address and its login, the default one for the site's own mail, removes both, and lists without logins`() {
        val kept = SendingAddress("events@esa-blueshell.nl", "Events", "smtp.example.nl", 465, MailSecurity.SSL).also { it.id = 7 }
        whenever(repository.findById(7)).thenReturn(Optional.of(kept))
        whenever(repository.findAllByOrderByAddress()).thenReturn(listOf(kept))
        whenever(repository.existsById(7)).thenReturn(true)
        logins.write(7, login)
        val route = SendingRoute("events@esa-blueshell.nl", "Events", SmtpRoute("smtp.example.nl", 465, MailSecurity.SSL, login))

        assertThat(addresses.routeFor(7)).isEqualTo(route)
        assertThat(addresses.defaultRoute()).isNull()
        whenever(repository.findFirstByIsDefaultTrue()).thenReturn(kept)
        assertThat(addresses.defaultRoute()).isEqualTo(route)
        assertThat(addresses.list().single().loginKept).isFalse()
        assertThat(addresses.exists(7)).isTrue()

        addresses.remove(7)
        verify(repository).delete(kept)
        assertThat(logins.read(7)).isNull()
        assertThatThrownBy { addresses.routeFor(7) }.isInstanceOf(SendingAddressNeedsLogin::class.java)
    }
}
