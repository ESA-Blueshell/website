package net.blueshell.api.domain.contribution.application.email

import net.blueshell.api.domain.contribution.domain.service.feeReason
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.email.EmailContent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Email builder for incasso notification (bulk collection notice).
 *
 * Builds EmailContent DTO that serves as Anti-Corruption Layer (ADR-019)
 * between the contribution domain and the platform email system.
 *
 * Incasso members are collected by DIRECT DEBIT (SEPA incasso), so this email
 * does NOT ask them to transfer any money. It notifies them that the fee will be
 * debited automatically. We know the applied fee, so a single amount is stated
 * together with the reason it applies, rather than listing every fee option.
 */

private val INCASSO_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH)

fun createIncassoNotificationEmail(
    recipient: User,
    contributionPeriod: ContributionPeriod,
    amount: Double,
    expectedIncassoDate: LocalDate,
    feeType: BulkFeeType,
): EmailContent {
    val academicYear = academicYearLabel(contributionPeriod)
    val formattedDate = expectedIncassoDate.format(INCASSO_DATE_FORMATTER)
    // Assembled from column-0 lines (not a trimIndent()-ed raw string) so no line
    // ever reaches the Markdown converter with leading whitespace, which would be
    // rendered as an indented code block.
    val markdownContent = buildList {
        add("Dear ${recipient.fullName},")
        add("")
        add(
            "Your membership fee for your $academicYear membership of ESA Blueshell will be automatically " +
                "subtracted from your bank account on or around **$formattedDate**. Please make sure there " +
                "are sufficient funds in your account on that date."
        )
        add("")
        add("**Amount to be collected: €${"%.2f".format(amount)}** (${feeReason(feeType)})")
        add("")
        add(
            "If you wish to terminate your membership, please respond to this email before $formattedDate " +
                "so we can remove you from the incasso list."
        )
        add("")
        add("Kind regards,")
        add("Secretary & Treasurer of ESA Blueshell")
    }.joinToString("\n")

    return EmailContent(
        recipientEmail = recipient.email,
        recipientName = recipient.fullName,
        subject = "Your Blueshell contribution will be collected automatically ($academicYear)",
        markdownContent = markdownContent,
        senderNameOverride = "Secretary & Treasurer of ESA Blueshell",
        replyToOverride = "board@blueshell.utwente.nl"
    )
}
