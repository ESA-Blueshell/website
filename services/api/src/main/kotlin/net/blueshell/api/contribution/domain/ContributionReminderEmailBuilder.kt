package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.user.persistence.User
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The payment request: what a member who pays by transfer is asked for.
 *
 * Builds an [EmailContent], the anti-corruption layer between this domain and the platform
 * email system (ADR-019), which is also what makes it previewable. The body is joined from
 * column-0 lines rather than a `trimIndent()`-ed raw string: interpolating the multi-line bank
 * block collapses the common indent to zero, and Markdown then renders the whole body as an
 * indented code block.
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

/**
 * How direct debit is offered. A property of the email, not of the member.
 */
internal enum class DirectDebitOffer {
    /** Nothing is owed yet, so a mandate is a way to pay the amount at hand. */
    SETTLES_THIS_ASK,

    /** An amount is already due, and a mandate arranges the years after this one. */
    ARRANGES_FUTURE_YEARS,
}

/** What a member writes in a transfer description or on an envelope, so we can match it to them. */
private fun paymentReference(academicYear: String): String =
    "your name, your student number if you have one, and \"contribution $academicYear\""

/**
 * The three ways the association takes money, as unindented Markdown lines.
 *
 * Direct debit is framed by [offer] rather than left to the reader to interpret. On a reminder
 * a mandate cannot settle what is being asked for — no debit run exists to collect it — so a
 * member who signs one instead of transferring pays nothing and is chased for money they
 * believe they have arranged. That is the mirror of the double-payment hazard that keeps the
 * incasso notification a separate email, and the reason this is a parameter rather than one
 * paragraph reused verbatim.
 */
internal fun paymentMethodLines(
    channels: PaymentChannels,
    academicYear: String,
    offer: DirectDebitOffer,
): List<String> {
    val bank = channels.bank
    val reference = paymentReference(academicYear)
    val mandate = "the direct debit mandate from our [association documents](${channels.documentsUrl})"
    return buildList {
        add("**Bank transfer**")
        add("Account: ${bank.iban}, in the name of ${bank.accountName}.")
        add("For foreign bank accounts, the BIC code is ${bank.bic}.")
        add("Please put $reference in the description.")
        add("")
        add("**Cash**")
        add(
            "The fee can be deposited in postbus 49 in the Bastille. Put the money in an envelope " +
                "and write $reference on it.",
        )
        add("")
        add("**Direct debit**")
        when (offer) {
            DirectDebitOffer.SETTLES_THIS_ASK -> add(
                "Fill in $mandate and email it to $REPLY_TO. We collect the fee once the mandate " +
                    "reaches us, and you do not need to transfer anything yourself.",
            )

            DirectDebitOffer.ARRANGES_FUTURE_YEARS -> add(
                "Rather not do this again next year? Fill in $mandate and email it to $REPLY_TO, " +
                    "and we collect your contribution automatically from next year onwards. A mandate " +
                    "does not settle what is asked for above, so please still pay that by transfer or " +
                    "in cash.",
            )
        }
    }
}

/**
 * The bulk payment request: one amount, the reason it applies, and the date it is due.
 *
 * The reason is never omitted, an amount on its own inviting the reply asking why. The amount
 * is passed in rather than priced from [feeType]: a sent request records what it stated, and
 * this quotes that rather than whatever the period's fee has since become.
 */
fun createContributionReminderEmail(
    recipient: User,
    contributionPeriod: ContributionPeriod,
    feeType: BulkFeeType,
    amount: Double,
    paymentDueDate: LocalDate,
    channels: PaymentChannels,
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
        addAll(paymentMethodLines(channels, academicYear, DirectDebitOffer.ARRANGES_FUTURE_YEARS))
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
 * amount quoted with a reason that would have to be guessed. Nothing on the site takes money,
 * so the mail must carry real payment instructions rather than point at a page.
 */
fun createContributionReminderEmail(
    recipient: User,
    contributionPeriod: ContributionPeriod,
    channels: PaymentChannels,
): EmailContent {
    val academicYear = academicYearLabel(contributionPeriod)
    val markdownContent = buildList {
        add("Dear ${recipient.fullName},")
        add("")
        add(
            "This is a friendly reminder that your contribution payment for the period " +
                "${contributionPeriod.startDate} to ${contributionPeriod.endDate} is due.",
        )
        add("")
        add("Payment options:")
        add("- Half year fee: €${formatEuros(contributionPeriod.halfYearFee)}")
        add("- Full year fee: €${formatEuros(contributionPeriod.fullYearFee)}")
        add("- Alumni fee: €${formatEuros(contributionPeriod.alumniFee)}")
        add("")
        addAll(paymentMethodLines(channels, academicYear, DirectDebitOffer.ARRANGES_FUTURE_YEARS))
        add("")
        add("If you have already made your payment, please disregard this message.")
        add("")
        add("Kind regards,")
        add("Treasurer of Blueshell Esports")
    }.joinToString("\n")

    return EmailContent(
        recipientEmail = recipient.email,
        recipientName = recipient.fullName,
        subject = "Contribution Payment Reminder - Blueshell Esports",
        markdownContent = markdownContent,
        senderNameOverride = "Treasurer of Blueshell",
        replyToOverride = REPLY_TO,
    )
}
