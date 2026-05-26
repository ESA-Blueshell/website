package net.blueshell.api.domain.contribution.application.email

import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.email.EmailContent

/**
 * Email builder for contribution payment reminders.
 *
 * Builds EmailContent DTO that serves as Anti-Corruption Layer (ADR-019)
 * between the contribution domain and the platform email system.
 */
fun createContributionReminderEmail(
    recipient: User,
    contributionPeriod: ContributionPeriod,
    frontendUrl: String
): EmailContent {
    val markdownContent = """
        Dear ${recipient.fullName},

        This is a friendly reminder that your contribution payment for the period ${contributionPeriod.startDate} to ${contributionPeriod.endDate} is due.

        Payment options:
        - Half year fee: €${"%.2f".format(contributionPeriod.halfYearFee)}
        - Full year fee: €${"%.2f".format(contributionPeriod.fullYearFee)}
        - Alumni fee: €${"%.2f".format(contributionPeriod.alumniFee)}

        Please make your payment at your earliest convenience via our [website]($frontendUrl).

        If you have already made your payment, please disregard this message.

        Kind regards,
        Treasurer of Blueshell Esports
    """.trimIndent()

    return EmailContent(
        recipientEmail = recipient.email,
        recipientName = recipient.fullName,
        subject = "Contribution Payment Reminder - Blueshell Esports",
        markdownContent = markdownContent,
        senderNameOverride = "Treasurer of Blueshell",
        replyToOverride = "board@blueshell.utwente.nl"
    )
}
