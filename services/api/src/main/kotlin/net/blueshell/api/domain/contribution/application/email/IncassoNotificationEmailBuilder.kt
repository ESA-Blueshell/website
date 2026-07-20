package net.blueshell.api.domain.contribution.application.email

import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.email.EmailContent
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Email builder for incasso notification (bulk collection notice).
 *
 * Builds EmailContent DTO that serves as Anti-Corruption Layer (ADR-019)
 * between the contribution domain and the platform email system.
 *
 * Incasso members are collected by DIRECT DEBIT (SEPA incasso), so this email
 * does NOT ask them to transfer any money. It notifies them that the fee will be
 * debited automatically and that no action is required on their part.
 */

private val INCASSO_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy")

fun createIncassoNotificationEmail(
    recipient: User,
    contributionPeriod: ContributionPeriod,
    amount: Double,
    expectedIncassoDate: LocalDate,
): EmailContent {
    val academicYear = academicYearLabel(contributionPeriod)
    val markdownContent = """
        Dear ${recipient.fullName},

        This is a notice about your Blueshell membership contribution for $academicYear. You are registered for automatic collection (SEPA direct debit), so there is no need to transfer any money yourself.

        The amount below will be debited automatically from your bank account on or around **${expectedIncassoDate.format(INCASSO_DATE_FORMATTER)}**. Please make sure that there are sufficient funds in your account on that date.

        **Amount to be collected: €${"%.2f".format(amount)}**

        No action is required on your part. If any of your details have changed, or if you have questions about this collection, please contact us.

        Kind regards,
        Secretary & Treasurer of ESA Blueshell
    """.trimIndent()

    return EmailContent(
        recipientEmail = recipient.email,
        recipientName = recipient.fullName,
        subject = "Your Blueshell contribution will be collected automatically ($academicYear)",
        markdownContent = markdownContent,
        senderNameOverride = "Secretary & Treasurer of ESA Blueshell",
        replyToOverride = "board@blueshell.utwente.nl"
    )
}
