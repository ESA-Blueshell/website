package net.blueshell.api.email.persistence

import net.blueshell.api.shared.enums.EmailDeliveryStatus
import net.blueshell.api.shared.repository.BaseRepository

interface EmailRepository : BaseRepository<Email, Long> {
    fun countByDeliveryStatus(status: EmailDeliveryStatus): Long

    fun findByTrackingToken(trackingToken: String): Email?

    /** The email a job sends; the newest where a job ever wrote two. */
    fun findTopByJobExecutionIdOrderByIdDesc(jobExecutionId: Long): Email?

    fun findByMessageId(messageId: String): Email?
}
