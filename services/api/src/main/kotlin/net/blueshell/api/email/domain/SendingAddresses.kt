package net.blueshell.api.email.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.email.api.KnownSendingAddresses
import net.blueshell.api.email.persistence.MailSecurity
import net.blueshell.api.email.persistence.SendingAddress
import net.blueshell.api.email.persistence.SendingAddressRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/** A sending address as the site shows it: never its login, only whether one is kept, and what its last check found. */
@Schema(name = "SendingAddress")
data class SendingAddressView(
    val id: Long,
    val address: String,
    val displayName: String,
    val host: String,
    val port: Int,
    val security: MailSecurity,
    @param:Schema(description = "Its IMAP server; absent where the address is not read")
    val imapHost: String?,
    val imapPort: Int?,
    val imapSecurity: MailSecurity?,
    @param:Schema(description = "Whether it sends the site's own mail and is picked first when writing")
    val isDefault: Boolean,
    @param:Schema(description = "Whether a login is kept for it; the login itself is never answered")
    val loginKept: Boolean,
    @param:Schema(description = "Whether the last check could send through it; absent until checked")
    val canSend: Boolean?,
    @param:Schema(description = "Whether the last check could read its mailbox; absent until checked or where it is not read")
    val canRead: Boolean?,
    @param:Schema(description = "What the SMTP server said when the last check could not send")
    val sendFailure: String?,
    @param:Schema(description = "What the IMAP server said when the last check could not read")
    val readFailure: String?,
    val checkedAt: Instant?,
)

/** What the board sets on a sending address. A blank username and password keep the login there is. */
data class SendingAddressChange(
    val address: String,
    val displayName: String,
    val host: String,
    val port: Int,
    val security: MailSecurity,
    val isDefault: Boolean,
    val username: String?,
    val password: String?,
    val imapHost: String? = null,
    val imapPort: Int? = null,
    val imapSecurity: MailSecurity? = null,
)

/** Who an email from an address is from, and the route it goes out by. */
data class SendingRoute(
    val address: String,
    val displayName: String,
    val route: SmtpRoute,
)

/**
 * The addresses the site sends from and reads (api ADR-039). A login is tried against the address's
 * servers before it is kept, kept only in [SendingLogins], and read back only to send, read or
 * check. The default address sends the site's own mail.
 */
@Service
class SendingAddresses(
    private val addresses: SendingAddressRepository,
    private val logins: SendingLogins,
    private val probe: MailProbe,
    private val clock: Clock,
) : KnownSendingAddresses {
    override fun exists(id: Long): Boolean = addresses.existsById(id)

    fun list(): List<SendingAddressView> = addresses.findAllByOrderByAddress().map(::viewOf)

    @Transactional
    fun add(change: SendingAddressChange): SendingAddressView {
        val address = change.address.trim()
        if (addresses.existsByAddressIgnoreCase(address)) throw SendingAddressTaken(address)
        val login = loginOf(change) ?: throw SendingAddressNeedsLogin()
        val reading = imapOf(change)
        tryServers(change, reading, login)
        val saved =
            addresses.save(
                SendingAddress(address, change.displayName.trim(), change.host.trim(), change.port, change.security).also {
                    it.read(reading)
                },
            )
        keep(saved, login)
        passed(saved)
        if (change.isDefault) makeDefault(saved)
        return viewOf(saved)
    }

    @Transactional
    fun update(
        id: Long,
        change: SendingAddressChange,
    ): SendingAddressView {
        val found = find(id)
        val address = change.address.trim()
        val renamed = !found.address.equals(address, ignoreCase = true)
        if (renamed && addresses.existsByAddressIgnoreCase(address)) throw SendingAddressTaken(address)
        val newLogin = loginOf(change)
        val reading = imapOf(change)
        // A kept login only ever goes to the servers it was tried on: replaying it to a new one would hand it to whoever runs that server.
        if (moved(found, change, reading) && newLogin == null) throw SendingAddressNeedsLogin()
        newLogin?.let { tryServers(change, reading, it) }
        found.address = address
        found.displayName = change.displayName.trim()
        found.host = change.host.trim()
        found.port = change.port
        found.security = change.security
        found.read(reading)
        newLogin?.let {
            keep(found, it)
            passed(found)
        }
        if (change.isDefault) makeDefault(found) else found.isDefault = false
        return viewOf(addresses.save(found))
    }

    @Transactional
    fun remove(id: Long) {
        val found = find(id)
        logins.delete(id)
        addresses.delete(found)
    }

    /**
     * Tries the address's servers with the login kept and records what they said, so the page can say
     * whether it sends and is read without anybody waiting on a mail server. A failure is an answer, never a refusal.
     */
    @Transactional
    fun check(id: Long): SendingAddressView {
        val found = find(id)
        // Vault out of reach is what the check found, not a reason to refuse it.
        val (login, missing) =
            try {
                logins.read(id) to NO_LOGIN
            } catch (e: SendingLoginsUnavailable) {
                null to e.summary
            }
        found.sendFailure = outcome(login, missing) { probe.sends(SmtpRoute(found.host, found.port, found.security, it)) }
        found.canSend = found.sendFailure == null
        val reading = found.reading()
        found.readFailure =
            reading?.let { outcome(login, missing) { probe.reads(ImapRoute(reading.host, reading.port, reading.security, it)) } }
        found.canRead = reading?.let { found.readFailure == null }
        found.checkedAt = clock.instant()
        return viewOf(addresses.save(found))
    }

    /** Who an email from [id] is from and how it goes out. Throws when the address or its login is gone. */
    fun routeFor(id: Long): SendingRoute {
        val found = find(id)
        val login = logins.read(id) ?: throw SendingAddressNeedsLogin()
        return SendingRoute(found.address, found.displayName, SmtpRoute(found.host, found.port, found.security, login))
    }

    /** The default address's route, which the site's own mail goes out by; none while no address is the default. */
    fun defaultRoute(): SendingRoute? = addresses.findFirstByIsDefaultTrue()?.id?.let(::routeFor)

    private fun tryServers(
        change: SendingAddressChange,
        reading: Reading?,
        login: SmtpLogin,
    ) {
        probe.sends(SmtpRoute(change.host.trim(), change.port, change.security, login))
        reading?.let { probe.reads(ImapRoute(it.host, it.port, it.security, login)) }
    }

    private fun moved(
        found: SendingAddress,
        change: SendingAddressChange,
        reading: Reading?,
    ) = found.host != change.host.trim() ||
        found.port != change.port ||
        found.security != change.security ||
        reading != found.reading()

    // A fresh login has just been tried on both servers, so the address is known to send and, where it is read, to be read.
    private fun passed(address: SendingAddress) {
        address.canSend = true
        address.sendFailure = null
        address.canRead = if (address.imapHost != null) true else null
        address.readFailure = null
        address.checkedAt = clock.instant()
    }

    private fun outcome(
        login: SmtpLogin?,
        missing: String,
        attempt: (SmtpLogin) -> Unit,
    ): String? {
        if (login == null) return missing
        return try {
            attempt(login)
            null
        } catch (e: SendingAddressRefusal) {
            (e.facts["reason"] as? String) ?: e.summary
        }
    }

    private fun keep(
        address: SendingAddress,
        login: SmtpLogin,
    ) {
        logins.write(requireNotNull(address.id), login)
        address.loginKeptAt = clock.instant()
    }

    private fun makeDefault(address: SendingAddress) {
        addresses.findAll().filter { it.id != address.id && it.isDefault }.forEach { it.isDefault = false }
        address.isDefault = true
    }

    private fun find(id: Long): SendingAddress = addresses.findById(id).orElseThrow { SendingAddressNotFound() }

    private fun loginOf(change: SendingAddressChange): SmtpLogin? {
        val username = change.username?.trim().orEmpty()
        val password = change.password.orEmpty()
        return if (username.isEmpty() || password.isEmpty()) null else SmtpLogin(username, password)
    }

    private fun viewOf(address: SendingAddress) =
        SendingAddressView(
            id = requireNotNull(address.id),
            address = address.address,
            displayName = address.displayName,
            host = address.host,
            port = address.port,
            security = address.security,
            imapHost = address.imapHost,
            imapPort = address.imapPort,
            imapSecurity = address.imapSecurity,
            isDefault = address.isDefault,
            loginKept = address.loginKeptAt != null,
            canSend = address.canSend,
            canRead = address.canRead,
            sendFailure = address.sendFailure,
            readFailure = address.readFailure,
            checkedAt = address.checkedAt,
        )

    private companion object {
        const val NO_LOGIN = "No login is kept."
    }
}

/** An address's IMAP server, all three parts or none. */
private data class Reading(
    val host: String,
    val port: Int,
    val security: MailSecurity,
)

private fun imapOf(change: SendingAddressChange): Reading? {
    val host = change.imapHost?.trim().orEmpty()
    if (host.isEmpty()) return null
    return Reading(host, change.imapPort ?: throw ImapServerIncomplete(), change.imapSecurity ?: throw ImapServerIncomplete())
}

private fun SendingAddress.reading(): Reading? {
    val host = imapHost ?: return null
    return Reading(host, imapPort ?: return null, imapSecurity ?: return null)
}

private fun SendingAddress.read(reading: Reading?) {
    imapHost = reading?.host
    imapPort = reading?.port
    imapSecurity = reading?.security
}
