package net.blueshell.api.email.domain

import jakarta.mail.MessagingException
import jakarta.mail.Session
import net.blueshell.api.email.persistence.MailSecurity
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Properties

/** The two ways the site talks to an address's mail servers. */
enum class MailProtocol { SMTP, IMAP }

/** Where and as whom an email from a sending address goes out. */
data class SmtpRoute(
    val host: String,
    val port: Int,
    val security: MailSecurity,
    val login: SmtpLogin,
)

/** Where and as whom an address's mailbox is read. */
data class ImapRoute(
    val host: String,
    val port: Int,
    val security: MailSecurity,
    val login: SmtpLogin,
)

private const val CONNECT_TIMEOUT_MS = "10000"
private const val READ_TIMEOUT_MS = "30000"

/**
 * A mail sender for one route, with the same timeouts the site's own sender keeps. The certificate
 * must name the host, so a login is never handed to a server merely in the way.
 */
internal fun senderFor(route: SmtpRoute): JavaMailSenderImpl =
    JavaMailSenderImpl().apply {
        host = route.host
        port = route.port
        username = route.login.username
        password = route.login.password
        // Plain smtp with ssl.enable rather than smtps, so the mail.smtp.* settings below apply to every route.
        protocol = "smtp"
        javaMailProperties.putAll(
            mapOf(
                "mail.smtp.auth" to "true",
                "mail.smtp.ssl.enable" to (route.security == MailSecurity.SSL).toString(),
                "mail.smtp.ssl.checkserveridentity" to "true",
                "mail.smtp.starttls.enable" to (route.security == MailSecurity.STARTTLS).toString(),
                "mail.smtp.starttls.required" to (route.security == MailSecurity.STARTTLS).toString(),
                "mail.smtp.connectiontimeout" to CONNECT_TIMEOUT_MS,
                "mail.smtp.timeout" to READ_TIMEOUT_MS,
                "mail.smtp.writetimeout" to READ_TIMEOUT_MS,
            ),
        )
    }

/** A session for reading one route's mailbox, held to the same rules as sending: the certificate names the host. */
internal fun sessionFor(route: ImapRoute): Session =
    Session.getInstance(
        Properties().apply {
            putAll(
                mapOf(
                    "mail.store.protocol" to "imap",
                    "mail.imap.ssl.enable" to (route.security == MailSecurity.SSL).toString(),
                    "mail.imap.ssl.checkserveridentity" to "true",
                    "mail.imap.starttls.enable" to (route.security == MailSecurity.STARTTLS).toString(),
                    "mail.imap.starttls.required" to (route.security == MailSecurity.STARTTLS).toString(),
                    "mail.imap.connectiontimeout" to CONNECT_TIMEOUT_MS,
                    "mail.imap.timeout" to READ_TIMEOUT_MS,
                ),
            )
        },
    )

/** Tries a login against an address's servers, before it is kept and whenever the address is checked. */
interface MailProbe {
    /** Throws [MailLoginRefused] with the server's answer when the SMTP server does not take the login. */
    fun sends(route: SmtpRoute)

    /** Throws [MailLoginRefused] with the server's answer when the IMAP server does not take the login. */
    fun reads(route: ImapRoute)
}

/**
 * Which servers an added address may have the site connect to: a mail port on a server on the
 * internet, or one of the site's own servers named in configuration. A board member types the host
 * and port, so without this any service inside the cluster could be reached and its answer read back.
 */
@Component
class MailServers(
    @param:Value($$"${email.sending.ports:25,465,587,2525,143,993}") private val ports: List<Int>,
    @Value($$"${email.sending.internal-hosts:}") internalHosts: List<String>,
) {
    private val ownHosts = internalHosts.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    /**
     * Throws unless the site may connect to [host] on [port], and send a login there with [security].
     * Held each time a login is tried, an email is sent and a mailbox is read, not only when saved.
     */
    fun allow(
        protocol: MailProtocol,
        host: String,
        port: Int,
        security: MailSecurity,
    ) {
        val named = host.trim().lowercase()
        if (port !in ports) throw MailServerNotAllowed(protocol, host, port)
        val resolved =
            try {
                InetAddress.getAllByName(named).toList()
            } catch (_: UnknownHostException) {
                // One of the site's own is let through to fail on connect, but never with a plain login.
                if (named in ownHosts && security != MailSecurity.NONE) return
                throw MailServerUnknown(protocol, host)
            }
        val inside = resolved.any(::internal)
        if (inside && named !in ownHosts) throw MailServerNotAllowed(protocol, host, port)
        if (security == MailSecurity.NONE && !resolved.all(::internal)) throw MailNeedsEncryption(protocol, host)
    }
}

/** Whether [address] is on a private, local or reserved network rather than the internet. */
internal fun internal(address: InetAddress): Boolean =
    address.isLoopbackAddress ||
        address.isSiteLocalAddress ||
        address.isLinkLocalAddress ||
        address.isAnyLocalAddress ||
        address.isMulticastAddress ||
        RESERVED.any { it.holds(address.address) }

/** A block of addresses, such as 100.64.0.0/10. */
private class AddressRange(
    private val base: ByteArray,
    private val bits: Int,
) {
    fun holds(address: ByteArray): Boolean =
        address.size == base.size &&
            (0 until bits).all { bit ->
                val mask = 1 shl (Byte.SIZE_BITS - 1 - bit % Byte.SIZE_BITS)
                address[bit / Byte.SIZE_BITS].toInt() and mask == base[bit / Byte.SIZE_BITS].toInt() and mask
            }
}

private fun rangeOf(block: String): AddressRange {
    val (base, bits) = block.split("/")
    return AddressRange(InetAddress.getByName(base).address, bits.toInt())
}

// Ranges Java does not count as local: shared space a cluster may use, the testing block, and IPv6's private range.
private val RESERVED = listOf("100.64.0.0/10", "198.18.0.0/15", "fc00::/7").map(::rangeOf)

@Component
class JavaMailProbe(
    private val servers: MailServers,
) : MailProbe {
    override fun sends(route: SmtpRoute) =
        tried(MailProtocol.SMTP, route.host, route.port, route.security) { senderFor(route).testConnection() }

    override fun reads(route: ImapRoute) =
        tried(MailProtocol.IMAP, route.host, route.port, route.security) {
            sessionFor(route).getStore("imap").use { it.connect(route.host, route.port, route.login.username, route.login.password) }
        }

    private fun tried(
        protocol: MailProtocol,
        host: String,
        port: Int,
        security: MailSecurity,
        connect: () -> Unit,
    ) {
        servers.allow(protocol, host, port, security)
        try {
            connect()
        } catch (e: MessagingException) {
            throw MailLoginRefused(protocol, e.message?.take(REASON_MAX) ?: e.javaClass.simpleName).apply { initCause(e) }
        }
    }

    private companion object {
        const val REASON_MAX = 300
    }
}
