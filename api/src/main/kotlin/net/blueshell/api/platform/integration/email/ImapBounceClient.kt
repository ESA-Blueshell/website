package net.blueshell.api.platform.integration.email

/**
 * Anti-Corruption Layer for IMAP bounce mailbox access.
 * Abstracts IMAP protocol details from the bounce detection logic.
 */
interface ImapBounceClient {
    fun fetchUnseenMessages(): List<RawBounceMessage>
    fun markSeen(message: RawBounceMessage)
}

data class RawBounceMessage(
    val uid: Long,
    val subject: String,
    val contentType: String,
    val rawBytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RawBounceMessage) return false
        return uid == other.uid
    }

    override fun hashCode(): Int = uid.hashCode()
}
