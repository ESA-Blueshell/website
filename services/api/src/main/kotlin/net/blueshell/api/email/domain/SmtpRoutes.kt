package net.blueshell.api.email.domain

import jakarta.mail.MessagingException
import net.blueshell.api.email.persistence.SmtpSecurity
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.net.UnknownHostException

/** Where and as whom an email from a sending address goes out. */
data class SmtpRoute(
    val host: String,
    val port: Int,
    val security: SmtpSecurity,
    val login: SmtpLogin,
)

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
                "mail.smtp.ssl.enable" to (route.security == SmtpSecurity.SSL).toString(),
                "mail.smtp.ssl.checkserveridentity" to "true",
                "mail.smtp.starttls.enable" to (route.security == SmtpSecurity.STARTTLS).toString(),
                "mail.smtp.starttls.required" to (route.security == SmtpSecurity.STARTTLS).toString(),
                "mail.smtp.connectiontimeout" to "10000",
                "mail.smtp.timeout" to "30000",
                "mail.smtp.writetimeout" to "30000",
            ),
        )
    }

/** Tries a login against its SMTP server before it is kept. */
fun interface SmtpProbe {
    /** Throws [SmtpLoginRefused] with the server's answer when the login does not work. */
    fun test(route: SmtpRoute)
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
class JavaMailSmtpProbe : SmtpProbe {
    override fun test(route: SmtpRoute) {
        if (route.security == SmtpSecurity.NONE && !onThisNetwork(route.host)) throw SmtpNeedsEncryption(route.host)
        try {
            senderFor(route).testConnection()
        } catch (e: MessagingException) {
            throw SmtpLoginRefused(e.message?.take(REASON_MAX) ?: e.javaClass.simpleName).apply { initCause(e) }
        }
    }

    private companion object {
        const val REASON_MAX = 300
    }
}
