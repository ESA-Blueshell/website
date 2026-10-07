package net.blueshell.api.email.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.email.api.KnownSendingAddresses
import net.blueshell.api.email.persistence.SendingAddress
import net.blueshell.api.email.persistence.SendingAddressRepository
import net.blueshell.api.email.persistence.SmtpSecurity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** A sending address as the site shows it: never its login, only whether one is kept. */
@Schema(name = "SendingAddress")
data class SendingAddressView(
    val id: Long,
    val address: String,
    val displayName: String,
    val host: String,
    val port: Int,
    val security: SmtpSecurity,
    val isDefault: Boolean,
    @param:Schema(description = "Whether a login is kept for it; the login itself is never answered")
    val loginKept: Boolean,
)

/** What an admin sets on a sending address. A blank username and password keep the login there is. */
data class SendingAddressChange(
    val address: String,
    val displayName: String,
    val host: String,
    val port: Int,
    val security: SmtpSecurity,
    val isDefault: Boolean,
    val username: String?,
    val password: String?,
)

/** Who an email from an added address is from, and the route it goes out by. */
data class SendingRoute(
    val address: String,
    val displayName: String,
    val route: SmtpRoute,
)

/**
 * The addresses the board may send written emails from (api ADR-039). A login is tried against its
 * server before it is kept, kept only in [SendingLogins], and read back only when an email goes out.
 */
@Service
class SendingAddresses(
    private val addresses: SendingAddressRepository,
    private val logins: SendingLogins,
    private val probe: SmtpProbe,
    private val clock: Clock,
) : KnownSendingAddresses {
    override fun exists(id: Long): Boolean = addresses.existsById(id)

    fun list(): List<SendingAddressView> = addresses.findAllByOrderByAddress().map(::viewOf)

    @Transactional
    fun add(change: SendingAddressChange): SendingAddressView {
        val address = change.address.trim()
        if (addresses.existsByAddressIgnoreCase(address)) throw SendingAddressTaken(address)
        val login = loginOf(change) ?: throw SendingAddressNeedsLogin()
        probe.test(SmtpRoute(change.host.trim(), change.port, change.security, login))
        val saved = addresses.save(SendingAddress(address, change.displayName.trim(), change.host.trim(), change.port, change.security))
        keep(saved, login)
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
        val moved = found.host != change.host.trim() || found.port != change.port || found.security != change.security
        // A kept login only ever goes to the server it was tried on: replaying it to a new one would hand it to whoever runs that server.
        if (moved && newLogin == null) throw SendingAddressNeedsLogin()
        newLogin?.let { probe.test(SmtpRoute(change.host.trim(), change.port, change.security, it)) }
        found.address = address
        found.displayName = change.displayName.trim()
        found.host = change.host.trim()
        found.port = change.port
        found.security = change.security
        newLogin?.let { keep(found, it) }
        if (change.isDefault) makeDefault(found) else found.isDefault = false
        return viewOf(addresses.save(found))
    }

    @Transactional
    fun remove(id: Long) {
        val found = find(id)
        logins.delete(id)
        addresses.delete(found)
    }

    /** Who an email from [id] is from and how it goes out. Throws when the address or its login is gone. */
    fun routeFor(id: Long): SendingRoute {
        val found = find(id)
        val login = logins.read(id) ?: throw SendingAddressNeedsLogin()
        return SendingRoute(found.address, found.displayName, SmtpRoute(found.host, found.port, found.security, login))
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
            requireNotNull(address.id),
            address.address,
            address.displayName,
            address.host,
            address.port,
            address.security,
            address.isDefault,
            address.loginKeptAt != null,
        )
}
