package net.blueshell.api.domain.contribution.application.email

import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.config.BankProperties
import net.blueshell.api.shared.email.EmailContent
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Email builder for contribution payment reminders.
 *
 * Builds EmailContent DTO that serves as Anti-Corruption Layer (ADR-019)
 * between the contribution domain and the platform email system.
 *
 * Members pay their membership contribution by BANK TRANSFER to the Blueshell
 * account, not via the website. The bank details come from configuration
 * (see BankProperties / blueshell.bank.*).
 */

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy")

/**
 * Derives an academic-year label (e.g. "2025/2026") from a contribution period.
 * When the period spans two calendar years the label uses both, otherwise it
 * falls back to a single year.
 */
internal fun academicYearLabel(period: ContributionPeriod): String {
    val startYear = period.startDate.year
    val endYear = period.endDate.year
    return if (endYear > startYear) "$startYear/$endYear" else "$startYear"
}

private fun bankTransferDetails(bank: BankProperties): String = """
        **Bank transfer**
        Account: ${bank.iban}, in the name of ${bank.accountName}.
        For foreign bank accounts, the BIC code is ${bank.bic}.
""".trimIndent()

private const val SIGN_OFF = "Secretary & Treasurer of ESA Blueshell"

/**
 * Bulk reminder email: quote a single resolved amount and due date, and ask the
 * member to pay by bank transfer to the Blueshell account.
 */
fun createContributionReminderEmail(
    recipient: User,
    contributionPeriod: ContributionPeriod,
    amount: Double,
    paymentDueDate: LocalDate,
    bank: BankProperties,
): EmailContent {
    val academicYear = academicYearLabel(contributionPeriod)
    val markdownContent = """
        Dear ${recipient.fullName},

        In order to retain your membership you will need to pay the contribution fee for $academicYear. This fee must be paid before **${paymentDueDate.format(DATE_FORMATTER)}**. If the payment is not received before this time, your membership role in our Discord and on the website will be revoked.

        The contribution may be paid by transferring the fee directly to the Blueshell bank account. Details are given below.

        **Amount due: €${"%.2f".format(amount)}**

        ${bankTransferDetails(bank)}

        If you have already paid, please disregard this message.

        Kind regards,
        $SIGN_OFF
    """.trimIndent()

    return EmailContent(
        recipientEmail = recipient.email,
        recipientName = recipient.fullName,
        subject = "Please pay your Blueshell contribution ($academicYear)",
        markdownContent = markdownContent,
        senderNameOverride = SIGN_OFF,
        replyToOverride = "board@blueshell.utwente.nl"
    )
}

/**
 * Single-user reminder email: the resolved amount is not known up front, so the
 * available fee options are listed. The member still pays by bank transfer to the
 * Blueshell account.
 */
fun createContributionReminderEmail(
    recipient: User,
    contributionPeriod: ContributionPeriod,
    bank: BankProperties,
): EmailContent {
    val academicYear = academicYearLabel(contributionPeriod)
    val markdownContent = """
        Dear ${recipient.fullName},

        In order to retain your membership you will need to pay the contribution fee for $academicYear. If the payment is not received in time, your membership role in our Discord and on the website will be revoked.

        The contribution may be paid by transferring the fee directly to the Blueshell bank account. The fee that applies to you is one of the following.

        **Fee options**
        - Half year fee: €${"%.2f".format(contributionPeriod.halfYearFee)}
        - Full year fee: €${"%.2f".format(contributionPeriod.fullYearFee)}
        - Alumni fee: €${"%.2f".format(contributionPeriod.alumniFee)}

        ${bankTransferDetails(bank)}

        If you have already paid, please disregard this message.

        Kind regards,
        $SIGN_OFF
    """.trimIndent()

    return EmailContent(
        recipientEmail = recipient.email,
        recipientName = recipient.fullName,
        subject = "Please pay your Blueshell contribution ($academicYear)",
        markdownContent = markdownContent,
        senderNameOverride = SIGN_OFF,
        replyToOverride = "board@blueshell.utwente.nl"
    )
}
