package net.blueshell.api.platform.integration.mock

import net.blueshell.api.platform.integration.email.ImapBounceClient
import net.blueshell.api.platform.integration.email.RawBounceMessage
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Test/dev double for [ImapBounceClient].
 * Provides an in-memory inbox that tests can populate via [enqueue].
 */
@Component
@Primary
@Profile("test | dev")
class MockImapBounceClient : ImapBounceClient {

    private val logger = LoggerFactory.getLogger(MockImapBounceClient::class.java)

    private val inbox = CopyOnWriteArrayList<RawBounceMessage>()
    private val _seenUids = CopyOnWriteArrayList<Long>()

    val seenUids: List<Long> get() = _seenUids.toList()

    fun enqueue(message: RawBounceMessage) {
        inbox.add(message)
        logger.debug("[imap-mock] enqueued message uid={} subject='{}'", message.uid, message.subject)
    }

    override fun fetchUnseenMessages(): List<RawBounceMessage> {
        return inbox.toList()
    }

    override fun markSeen(message: RawBounceMessage) {
        inbox.removeIf { it.uid == message.uid }
        _seenUids.add(message.uid)
        logger.debug("[imap-mock] marked seen uid={}", message.uid)
    }

    fun clear() {
        inbox.clear()
        _seenUids.clear()
    }
}
