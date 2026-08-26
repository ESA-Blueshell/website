package net.blueshell.api.platform.integration.email.web.dto

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
    @param:Schema(description = "Whether this email's body was stored, so it can be previewed")
    val previewable: Boolean,
)

@Schema(name = "SentEmailPreview")
data class SentEmailPreviewDTO(
    val subject: String,
    @param:Schema(description = "The email's html with every url stripped out")
    val html: String,
    val recipientEmail: String,
    val recipientName: String,
    @param:Schema(description = "Always true: the preview's links were removed before it left the api")
    val linksRedacted: Boolean = true,
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
