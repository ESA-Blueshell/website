package net.blueshell.api.email.domain

import jakarta.mail.MessagingException
import jakarta.mail.Session
import net.blueshell.api.email.persistence.MailSecurity
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
 * Whether a login may cross the wire unencrypted to [host]: only to a machine on this network,
 * never over the internet. A host that does not resolve is not.
 */
internal fun onThisNetwork(host: String): Boolean =
    try {
        InetAddress.getAllByName(host).all { it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress }
    } catch (_: UnknownHostException) {
        false
    }

@Component
class JavaMailProbe : MailProbe {
    override fun sends(route: SmtpRoute) = tried(MailProtocol.SMTP, route.host, route.security) { senderFor(route).testConnection() }

    override fun reads(route: ImapRoute) =
        tried(MailProtocol.IMAP, route.host, route.security) {
            sessionFor(route).getStore("imap").use { it.connect(route.host, route.port, route.login.username, route.login.password) }
        }

    private fun tried(
        protocol: MailProtocol,
        host: String,
        security: MailSecurity,
        connect: () -> Unit,
    ) {
        if (security == MailSecurity.NONE && !onThisNetwork(host)) throw MailNeedsEncryption(protocol, host)
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
