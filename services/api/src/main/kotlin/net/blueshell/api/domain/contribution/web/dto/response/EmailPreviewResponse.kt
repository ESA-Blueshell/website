package net.blueshell.api.domain.contribution.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema

/**
 * A rendered email preview: the subject line and the full HTML body that would be sent.
 * Returned by the reminder / incasso-notification preview endpoints so an operator can
 * double-check the actual email before a bulk send.
 */
@Schema(name = "EmailPreviewResponse")
data class EmailPreviewResponse(
    val subject: String,
    val html: String,
)
