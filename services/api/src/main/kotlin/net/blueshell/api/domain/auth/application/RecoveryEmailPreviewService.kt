package net.blueshell.api.domain.auth.application

import net.blueshell.api.domain.auth.application.email.PREVIEW_TOKEN_PLACEHOLDER
import net.blueshell.api.domain.auth.application.email.buildRecoveryEmail
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.email.api.EmailPreviewRenderer
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.model.RecoveryEmailPreview
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Renders a recovery email for inspection.
 *
 * Builds through the same [buildRecoveryEmail] a send uses and renders through the shared
 * preview renderer, then stops: no token is issued, no outbox row is written, no tracking
 * pixel is injected and nothing reaches the transport. A recovery link is a credential, so
 * the link carries [PREVIEW_TOKEN_PLACEHOLDER] rather than one that would work.
 */
@Service
class RecoveryEmailPreviewService(
    private val users: UserService,
    private val renderer: EmailPreviewRenderer,
    @param:Value($$"${frontend.url}") private val frontendUrl: String,
) {
    @Transactional(readOnly = true)
    fun preview(userId: Long, purpose: TokenPurpose): RecoveryEmailPreview {
        val user = users.findById(userId)
        val content = buildRecoveryEmail(purpose, user, PREVIEW_TOKEN_PLACEHOLDER, frontendUrl)
        val rendered = renderer.render(content)
        return RecoveryEmailPreview(
            purpose = purpose,
            subject = rendered.subject,
            html = rendered.html,
            recipientEmail = content.recipientEmail,
            recipientName = content.recipientName,
            linkPlaceholder = PREVIEW_TOKEN_PLACEHOLDER,
        )
    }
}
