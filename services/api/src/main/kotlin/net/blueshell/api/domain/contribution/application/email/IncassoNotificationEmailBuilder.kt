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
 */
fun createIncassoNotificationEmail(
    recipient: User,
    contributionPeriod: ContributionPeriod,
    amount: Double,
    expectedIncassoDate: LocalDate,
    frontendUrl: String
): EmailContent {
    val formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy")
    val markdownContent = """
        Dear ${recipient.fullName},

        This is to notify you that we will collect your membership contribution via direct debit.

        **Amount to be collected: €${"%.2f".format(amount)}**
        **Collection date: ${expectedIncassoDate.format(formatter)}**

        For the contribution period ${contributionPeriod.startDate} to ${contributionPeriod.endDate}.

        If you have any questions or concerns about this collection, please contact the treasurer at board@blueshell.utwente.nl.

        Kind regards,
        Treasurer of Blueshell Esports
    """.trimIndent()

    return EmailContent(
        recipientEmail = recipient.email,
        recipientName = recipient.fullName,
        subject = "Membership Contribution Collection Notice - Blueshell Esports",
        markdownContent = markdownContent,
        senderNameOverride = "Treasurer of Blueshell",
        replyToOverride = "board@blueshell.utwente.nl"
    )
}
