package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.platform.config.BankProperties
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.user.persistence.User
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The payment request: what a member who pays by transfer is asked for.
 *
 * Builds an [EmailContent], which is the anti-corruption layer between the contribution
 * domain and the platform email system (ADR-019), and is also what makes the email
 * previewable — `EmailPreviewRenderer` renders any `EmailContent`.
 *
 * The body is assembled from column-0 lines joined with newlines rather than a
 * `trimIndent()`-ed raw string. Interpolating a multi-line value (the bank block) into an
 * indented raw string defeats `trimIndent()`: the interpolated lines carry no indentation,
 * so the common indent collapses to 0 and every other line keeps its source indentation.
 * Markdown then renders the whole body as an indented code block.
 */

/** Members read these amounts in Dutch notation, so the separator is pinned rather than inherited from the JVM. */
private val MONEY_LOCALE: Locale = Locale.forLanguageTag("nl-NL")

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

/** Both bulk contribution emails are from the same pair of officers, and answered at the same address. */
internal const val SIGN_OFF = "Secretary & Treasurer of ESA Blueshell"

internal const val REPLY_TO = "board@blueshell.utwente.nl"

internal fun formatEuros(amount: Double): String = String.format(MONEY_LOCALE, "%.2f", amount)

internal fun formatDate(date: LocalDate): String = date.format(DATE_FORMATTER)

/**
 * The period's academic-year label, e.g. "2025/2026". A period inside one calendar year
 * gets that year alone rather than a range that reads as a mistake.
 */
internal fun academicYearLabel(period: ContributionPeriod): String {
    val startYear = period.startDate.year
    val endYear = period.endDate.year
    return if (endYear > startYear) "$startYear/$endYear" else "$startYear"
}

/** Where the money goes, as unindented Markdown lines. */
private fun bankTransferLines(bank: BankProperties): List<String> = listOf(
    "**Bank transfer**",
    "Account: ${bank.iban}, in the name of ${bank.accountName}.",
    "For foreign bank accounts, the BIC code is ${bank.bic}.",
)

/**
 * The bulk payment request: one amount, the reason it applies, and the date it is due.
 *
 * The reason is never omitted — an amount on its own invites the reply asking why it is
 * that amount, which is the question the cycle exists to answer up front.
 *
 * The amount is passed in rather than priced from [feeType] here, because a sent request
 * records the amount it stated and this has to quote that one, not whatever the period's
 * fee has since become.
 */
fun createContributionReminderEmail(
    recipient: User,
    contributionPeriod: ContributionPeriod,
    feeType: BulkFeeType,
    amount: Double,
    paymentDueDate: LocalDate,
    bank: BankProperties,
): EmailContent {
    val academicYear = academicYearLabel(contributionPeriod)
    val dueDate = formatDate(paymentDueDate)
    val markdownContent = buildList {
        add("Dear ${recipient.fullName},")
        add("")
        add(
            "In order to retain your membership you will need to pay the contribution fee for " +
                "$academicYear. This fee must be paid before **$dueDate**. If the payment is not " +
                "received before then, your membership role in our Discord and on the website will " +
                "be revoked.",
        )
        add("")
        add("**Amount due: €${formatEuros(amount)}** (${feeReason(feeType)})")
        add("")
        addAll(bankTransferLines(bank))
        add("")
        add("If you have already paid, please disregard this message.")
        add("")
        add("Kind regards,")
        add(SIGN_OFF)
    }.joinToString("\n")

    return EmailContent(
        recipientEmail = recipient.email,
        recipientName = recipient.fullName,
        subject = "Please pay your Blueshell contribution ($academicYear)",
        markdownContent = markdownContent,
        senderNameOverride = SIGN_OFF,
        replyToOverride = REPLY_TO,
    )
}

/**
 * Single-member reminder, sent from a row rather than in bulk.
 *
 * No fee type was chosen here, so the period's three options are listed rather than one
 * amount quoted with a reason that would have to be guessed. The two are different asks, and
 * this one's copy has stayed as it was.
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
        - Half year fee: €${formatEuros(contributionPeriod.halfYearFee)}
        - Full year fee: €${formatEuros(contributionPeriod.fullYearFee)}
        - Alumni fee: €${formatEuros(contributionPeriod.alumniFee)}

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
