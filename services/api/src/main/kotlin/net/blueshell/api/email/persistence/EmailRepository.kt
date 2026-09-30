package net.blueshell.api.email.persistence

import net.blueshell.api.shared.enums.EmailDeliveryStatus
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.domain.Pageable
import java.time.Instant

interface EmailRepository : BaseRepository<Email, Long> {
    fun countByDeliveryStatus(status: EmailDeliveryStatus): Long

    fun countByDeliveryStatusInAndCreatedAtAfter(
        statuses: Collection<EmailDeliveryStatus>,
        after: Instant,
    ): Long

    fun findTopByDeliveryStatusInAndCreatedAtAfterOrderByIdDesc(
        statuses: Collection<EmailDeliveryStatus>,
        after: Instant,
    ): Email?

    fun findByTrackingToken(trackingToken: String): Email?

    fun findByMessageId(messageId: String): Email?

    fun findByDeliveryStatusAndSentAtBefore(
        status: EmailDeliveryStatus,
        threshold: Instant,
        pageable: Pageable,
    ): List<Email>

    fun findTopByRecipientEmailAndSentAtAfterOrderBySentAtDesc(
        recipientEmail: String,
        since: Instant,
    ): Email?
}
