package net.blueshell.api.domain.contribution.application

import net.blueshell.api.domain.contribution.application.email.createContributionReminderEmail
import net.blueshell.api.domain.contribution.application.email.createIncassoNotificationEmail
import net.blueshell.api.domain.contribution.domain.service.resolveFeeAmount
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.config.BankProperties
import net.blueshell.api.platform.integration.email.application.service.EmailSenderService
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.email.EmailContent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Rendered email preview: the exact subject + HTML body that a reminder / incasso
 * notification would carry if sent, so an operator can double-check before a bulk run.
 */
data class RenderedEmailPreview(val subject: String, val html: String)

/**
 * Renders reminder / incasso-notification emails for ONE user WITHOUT sending or
 * persisting anything. It reuses the same pure email builders and the same template
 * render step the real send path uses (via [EmailSenderService.renderEmailHtml]), so the
 * preview is faithful to what would actually be delivered. It never creates a
 * ContributionReminder / IncassoNotification and never enqueues an email job.
 *
 * The effective fee type mirrors the execute path: an operator-supplied [feeType]
 * override is honored, otherwise the recommended type is resolved from the member's
 * latest-membership start relative to the half-year cutoff.
 */
@Service
class EmailPreviewService(
    private val users: UserService,
    private val periods: ContributionPeriodService,
    private val emailSender: EmailSenderService,
    private val bank: BankProperties,
) {
    /** Render the contribution-reminder email for [userId] using [paymentDueDate]. */
    @Transactional(readOnly = true)
    fun previewReminder(
        userId: Long,
        contributionPeriodId: Long,
        feeType: BulkFeeType,
        paymentDueDate: LocalDate,
    ): RenderedEmailPreview {
        val user = users.findById(userId)
        val period = periods.findById(contributionPeriodId)
        val amount = resolveFeeAmount(feeType, period)
        val content = createContributionReminderEmail(user, period, amount, paymentDueDate, bank, feeType)
        return render(content)
    }

    /** Render the incasso-notification email for [userId] using [expectedIncassoDate]. */
    @Transactional(readOnly = true)
    fun previewIncassoNotification(
        userId: Long,
        contributionPeriodId: Long,
        feeType: BulkFeeType,
        expectedIncassoDate: LocalDate,
    ): RenderedEmailPreview {
        val user = users.findById(userId)
        val period = periods.findById(contributionPeriodId)
        val amount = resolveFeeAmount(feeType, period)
        val content = createIncassoNotificationEmail(user, period, amount, expectedIncassoDate, feeType)
        return render(content)
    }

    private fun render(content: EmailContent): RenderedEmailPreview =
        RenderedEmailPreview(subject = content.subject, html = inlineEmailAssets(emailSender.renderEmailHtml(content)))

    /**
     * Preview-only: replace the hosted email-asset URLs with base64 data URIs read from
     * the classpath, so the preview iframe always shows the logo/watermark regardless of
     * whether the configured frontend URL is reachable from the operator's browser
     * (e.g. docker-internal hostnames in dev, or assets not yet deployed). The real send
     * path is untouched, so mail clients keep the hosted URLs.
     */
    private fun inlineEmailAssets(html: String): String {
        var result = html
        INLINEABLE_ASSETS.forEach { (urlSuffix, dataUri) ->
            if (dataUri != null) {
                // Match the full URL (absolute or relative) ending in the asset path, as it
                // appears in src/background attributes and CSS url(...) values.
                result = result.replace(Regex("""[^"'()\s]*${Regex.escape(urlSuffix)}"""), dataUri)
            }
        }
        return result
    }

    private companion object {
        /** Hosted-URL suffix -> data URI (null when the classpath asset is missing). */
        private val INLINEABLE_ASSETS: Map<String, String?> by lazy {
            mapOf(
                "/img/email/blueshell-logo.png" to classpathDataUri("templates/assets/BSLOGO.png"),
                "/img/email/watermark.png" to classpathDataUri("templates/assets/BackdropBlack.png"),
            )
        }

        private fun classpathDataUri(path: String): String? =
            EmailPreviewService::class.java.classLoader.getResourceAsStream(path)?.use {
                "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(it.readBytes())
            }
    }
}
