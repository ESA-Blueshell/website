package net.blueshell.api.platform.integration.email.application.service

import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.model.SentEmailPreview
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Renders an email from the outbox for inspection.
 *
 * Reads the stored body back through the same renderer the send path uses, so the operator
 * sees the email rather than a description of it, then strips every URL out of the result —
 * see [EmailUrlRedaction] for why that is not optional here.
 *
 * Rows written before the body was stored have nothing to render; the caller gets null and
 * says so, rather than being shown an empty template that looks like a delivery bug.
 */
@Service
class SentEmailPreviewService(
    private val emails: EmailService,
    private val renderer: EmailPreviewRenderer,
) {
    @Transactional(readOnly = true)
    fun preview(emailId: Long): SentEmailPreview? {
        val email = emails.findById(emailId)
        val body = email.bodyMarkdown ?: return null

        val rendered = renderer.render(
            EmailContent(
                recipientEmail = email.recipientEmail,
                recipientName = email.recipientName,
                subject = email.subject,
                markdownContent = body,
            ),
        )

        return SentEmailPreview(
            subject = rendered.subject,
            html = EmailUrlRedaction.redact(rendered.html),
            recipientEmail = email.recipientEmail,
            recipientName = email.recipientName,
        )
    }
}
