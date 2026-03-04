package net.blueshell.api.platform.integration.email

import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Session
import jakarta.mail.UIDFolder
import jakarta.mail.search.FlagTerm
import net.blueshell.api.platform.config.ImapBounceProperties
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.util.Properties

/**
 * Production IMAP client that connects to the sender's mailbox to fetch
 * potential bounce/DSN messages. Uses stateless per-poll connections.
 */
@Component
@Profile("!test & !dev")
class ImapBounceClientImpl(
    private val bounceProperties: ImapBounceProperties,
) : ImapBounceClient {

    private val logger = LoggerFactory.getLogger(ImapBounceClientImpl::class.java)

    override fun fetchUnseenMessages(): List<RawBounceMessage> {
        val props = Properties().apply {
            put("mail.imaps.connectiontimeout", bounceProperties.connectionTimeoutMs.toString())
            put("mail.imaps.timeout", bounceProperties.readTimeoutMs.toString())
            if (bounceProperties.ssl) {
                put("mail.imaps.ssl.enable", "true")
            }
        }
        val session = Session.getInstance(props)
        val store = session.getStore("imaps")

        return try {
            store.connect(bounceProperties.host, bounceProperties.port, bounceProperties.username, bounceProperties.password)
            val folder = store.getFolder(bounceProperties.folder)
            folder.open(Folder.READ_ONLY)

            try {
                val unseenTerm = FlagTerm(Flags(Flags.Flag.SEEN), false)
                val messages = folder.search(unseenTerm)
                val uidFolder = folder as UIDFolder

                messages.take(bounceProperties.batchSize).mapNotNull { message ->
                    try {
                        val uid = uidFolder.getUID(message)
                        val baos = ByteArrayOutputStream()
                        message.writeTo(baos)
                        RawBounceMessage(
                            uid = uid,
                            subject = message.subject ?: "",
                            contentType = message.contentType ?: "",
                            rawBytes = baos.toByteArray(),
                        )
                    } catch (e: Exception) {
                        logger.warn("Failed to serialize IMAP message, skipping", e)
                        null
                    }
                }
            } finally {
                folder.close(false)
            }
        } finally {
            store.close()
        }
    }

    override fun markSeen(message: RawBounceMessage) {
        val props = Properties().apply {
            put("mail.imaps.connectiontimeout", bounceProperties.connectionTimeoutMs.toString())
            put("mail.imaps.timeout", bounceProperties.readTimeoutMs.toString())
            if (bounceProperties.ssl) {
                put("mail.imaps.ssl.enable", "true")
            }
        }
        val session = Session.getInstance(props)
        val store = session.getStore("imaps")

        try {
            store.connect(bounceProperties.host, bounceProperties.port, bounceProperties.username, bounceProperties.password)
            val folder = store.getFolder(bounceProperties.folder)
            folder.open(Folder.READ_WRITE)

            try {
                val uidFolder = folder as UIDFolder
                val msg = uidFolder.getMessageByUID(message.uid)
                if (msg != null) {
                    msg.setFlag(Flags.Flag.SEEN, true)
                } else {
                    logger.warn("Could not find IMAP message with UID {} to mark as seen", message.uid)
                }
            } finally {
                folder.close(false)
            }
        } finally {
            store.close()
        }
    }
}
