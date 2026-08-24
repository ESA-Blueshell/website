package net.blueshell.api.domain.auth.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.enums.TokenPurpose

@Schema(
    name = "RecoveryEmailPreviewResponse",
    description = "A recovery email rendered for inspection. No token was issued to produce it.",
)
data class RecoveryEmailPreviewResponse(
    @field:Schema(description = "Which recovery email this is.")
    val purpose: TokenPurpose,

    @field:Schema(description = "Subject line the recipient would see.", example = "Activate your Account")
    val subject: String,

    @field:Schema(description = "The rendered email, as delivered but for the inert recovery link.")
    val html: String,

    @field:Schema(description = "Address the email would be sent to.", example = "member@example.com")
    val recipientEmail: String,

    @field:Schema(description = "Name the email addresses the recipient by.", example = "Alice Regular")
    val recipientName: String,

    @field:Schema(
        description = "What the recovery link carries in place of a token, so the preview can say the link is inert.",
        example = "PREVIEW-ONLY-NO-TOKEN-ISSUED",
    )
    val linkPlaceholder: String,
)
