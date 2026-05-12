package net.blueshell.systemtests

import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMultipart
import java.util.Properties

/**
 * IMAP-backed inspection helper for tests that need to assert what the
 * api delivered to the mail server. Connects to the IMAP listener
 * Stalwart exposes (default port 143 inside the network, 1143 from the
 * host in dev), authenticates as the admin user, and queries the
 * configured inbox.
 *
 * Mirrors the call surface tests previously got from the in-process
 * `MockListmonkEmailClient`: `reset()` empties the inbox before each
 * test, `findEmail(...)` returns the first matching delivery,
 * `assertEmailSent(...)` polls until one arrives or the timeout
 * elapses.
 *
 * The inbox the api writes to is set via `-Dtest.imap.mailbox=` (default
 * `bounce@dev.local`, the seeded mailbox in the dev compose). For tests
 * to find their messages, the api's outbound mail must reach that
 * mailbox — either by sending directly to it or via a catchall in the
 * Stalwart directory.
 */
class StalwartMailClient(
    private val host: String = System.getProperty("test.imap.host", "localhost"),
    private val port: Int = System.getProperty("test.imap.port", "1143").toInt(),
    private val username: String = System.getProperty("test.imap.user", "bounce@dev.local"),
    private val password: String = System.getProperty("test.imap.password", "bounce"),
    private val folderName: String = System.getProperty("test.imap.folder", "INBOX"),
) {
    private val session: Session by lazy {
        val props = Properties().apply {
            put("mail.store.protocol", "imap")
            put("mail.imap.host", host)
            put("mail.imap.port", port.toString())
            put("mail.imap.connectiontimeout", DEFAULT_TIMEOUT_MS.toString())
            put("mail.imap.timeout", DEFAULT_TIMEOUT_MS.toString())
            put("mail.imap.starttls.enable", "false")
            put("mail.imap.ssl.enable", "false")
        }
        Session.getInstance(props)
    }

    /** Mark every message in the target folder as deleted and expunge. */
    fun reset() {
        withFolder(write = true) { folder ->
            val messages = folder.messages
            if (messages.isNotEmpty()) {
                folder.setFlags(messages, Flags(Flags.Flag.DELETED), true)
            }
        }
    }

    /**
     * First message in the inbox whose recipient list contains
     * `recipient` and whose subject matches `subject` exactly. Returns
     * null when no such message exists.
     */
    fun findEmail(recipient: String, subject: String): DeliveredEmail? =
        withFolder(write = false) { folder ->
            folder.messages.firstOrNull { msg ->
                msg.subject == subject && msg.recipientsContains(recipient)
            }?.toDeliveredEmail()
        }

    /**
     * Polls `findEmail` until a match arrives or the deadline passes.
     * Times out with an `AssertionError` describing what was expected.
     */
    fun assertEmailSent(
        recipient: String,
        subject: String,
        timeoutMs: Long = 10_000,
        pollIntervalMs: Long = 250,
    ): DeliveredEmail {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val hit = findEmail(recipient, subject)
            if (hit != null) return hit
            Thread.sleep(pollIntervalMs)
        }
        throw AssertionError(
            "Expected email subject=\"$subject\" recipient=$recipient " +
                "within ${timeoutMs}ms but none arrived",
        )
    }

    private fun <T> withFolder(write: Boolean, block: (Folder) -> T): T {
        val store = session.getStore("imap")
        store.connect(host, port, username, password)
        try {
            val folder = store.getFolder(folderName)
            val mode = if (write) Folder.READ_WRITE else Folder.READ_ONLY
            folder.open(mode)
            try {
                return block(folder)
            } finally {
                // expunge = true on close commits the DELETED flags
                folder.close(write)
            }
        } finally {
            store.close()
        }
    }

    private fun Message.recipientsContains(recipient: String): Boolean {
        val all = (
            (getRecipients(Message.RecipientType.TO) ?: emptyArray()) +
                (getRecipients(Message.RecipientType.CC) ?: emptyArray()) +
                (getRecipients(Message.RecipientType.BCC) ?: emptyArray())
        )
        return all.any { (it as? InternetAddress)?.address?.equals(recipient, ignoreCase = true) == true }
    }

    private fun Message.toDeliveredEmail(): DeliveredEmail {
        val to = (getRecipients(Message.RecipientType.TO) ?: emptyArray())
            .mapNotNull { (it as? InternetAddress)?.address }
        val body = when (val content = content) {
            is String -> content
            is MimeMultipart -> extractText(content) ?: ""
            else -> content?.toString().orEmpty()
        }
        return DeliveredEmail(
            subject = subject ?: "",
            recipients = to,
            body = body,
        )
    }

    private fun extractText(multipart: MimeMultipart): String? {
        for (i in 0 until multipart.count) {
            val part = multipart.getBodyPart(i)
            if (part.isMimeType("text/plain")) return part.content?.toString()
        }
        for (i in 0 until multipart.count) {
            val part = multipart.getBodyPart(i)
            if (part.isMimeType("text/html")) return part.content?.toString()
        }
        return null
    }

    data class DeliveredEmail(
        val subject: String,
        val recipients: List<String>,
        val body: String,
    )

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 5_000
    }
}
