package net.blueshell.api.platform.integration.email.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import java.time.Instant

@Schema(name = "Email")
data class EmailDTO(
    val id: Long?,
    val recipientEmail: String?,
    val recipientName: String?,
    val subject: String?,
    val emailType: String?,
    val deliveryStatus: EmailDeliveryStatus?,
    val messageId: String?,
    val sentAt: Instant?,
    val deliveredAt: Instant?,
    val openedAt: Instant?,
    val errorType: String?,
    val errorReason: String?,
    val attempts: Int?,
    val jobExecutionId: Long?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

@Schema(name = "EmailStats")
data class EmailStatsDTO(
    val totalCount: Long,
    val pendingCount: Long,
    val sentCount: Long,
    val deliveredCount: Long,
    val openedCount: Long,
    val bouncedCount: Long,
    val failedCount: Long,
)
