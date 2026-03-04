package net.blueshell.api.platform.integration.email.persistence.repository

import net.blueshell.api.platform.integration.email.persistence.EmailOutbox
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.domain.Pageable
import java.time.Instant

interface EmailOutboxRepository : BaseRepository<EmailOutbox, Long> {
    fun countByDeliveryStatus(status: EmailDeliveryStatus): Long

    fun findByTrackingToken(trackingToken: String): EmailOutbox?

    fun findByDeliveryStatusAndSentAtBefore(
        status: EmailDeliveryStatus,
        threshold: Instant,
        pageable: Pageable
    ): List<EmailOutbox>
}
