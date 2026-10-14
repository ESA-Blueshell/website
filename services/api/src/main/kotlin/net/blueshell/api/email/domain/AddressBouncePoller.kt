package net.blueshell.api.email.domain

import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.search.ComparisonTerm
import jakarta.mail.search.ReceivedDateTerm
import net.blueshell.api.email.api.AddressMailboxes
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.Date

/**
 * Takes the delivery reports that land in each sending address's own mailbox, the one its mail
 * went out from. That mailbox is someone's working inbox, so it is read without marking anything:
 * the last two days' mail each poll, and an email already bounced is left as it is.
 */
@Service
class AddressBouncePoller(
    private val mailboxes: AddressMailboxes,
    private val emailService: EmailService,
) {
    // How a message becomes a delivery report. A test swaps in its own.
    internal var parse: (Message) -> BounceMessageParser.ParsedBounce? = BounceMessageParser::parse

    @Scheduled(fixedDelayString = "\${email.bounce.poll-interval-ms:300000}")
    fun poll() =
        mailboxes.eachOpen(Folder.READ_ONLY) { address, folder ->
            val since = Date.from(Instant.now().minus(LOOK_BACK))
            folder.search(ReceivedDateTerm(ComparisonTerm.GE, since)).forEach { take(address, it) }
        }

    // Anyone can mail a report to the address, so it counts only for an email that went out from it.
    private fun take(
        address: String,
        message: Message,
    ) {
        try {
            val report = parse(message) ?: return
            val outbox = emailService.findByMessageId(report.originalMessageId) ?: return
            if (!address.equals(outbox.senderAddress, ignoreCase = true)) return
            if (outbox.deliveryStatus != EmailDeliveryStatus.BOUNCED) emailService.markBounced(outbox, report.describe())
        } catch (e: Exception) {
            log.warn("A message in a sending address's mailbox could not be read as a delivery report: {}", e.javaClass.simpleName)
        }
    }

    private companion object {
        val LOOK_BACK: Duration = Duration.ofDays(2)
        private val log = LoggerFactory.getLogger(AddressBouncePoller::class.java)
    }
}
