package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.user.persistence.User
import java.time.LocalDate

/**
 * The first ask: what a new member owes, and how to pay it.
 *
 * Sent the moment a membership starts through the signup form, which is the one point at
 * which nothing is owed yet. That is why this is the only payment email that can offer a
 * direct debit mandate as a way to settle the amount at hand — on a reminder the money is
 * already due and no debit run exists to collect it.
 *
 * The subject is also the headline: `EmailSenderService` passes it to the template as
 * `mainTitle`, so the two cannot be written separately.
 *
 * The deadline is a warning and nothing more. No job reads it and nothing revokes anything;
 * a board member follows it up. Nothing in the system can tell an unpaid contribution from
 * one the treasurer has not recorded yet, so an automatic consequence would eventually end
 * the membership of somebody who paid in cash.
 */
fun createJoiningContributionEmail(
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
        add("Dear ${recipient.firstName},")
        add("")
        add(
            "Welcome to ESA Blueshell. To finalise your membership you need to pay the contribution " +
                "fee for $academicYear. This fee must be paid before **$dueDate**. If the payment is " +
                "not received before then, your membership role in our Discord and on the website is " +
                "revoked.",
        )
        add("")
        add("**Amount due: €${formatEuros(amount)}** (${feeReason(feeType)})")
        add("")
        add("The contribution may be paid in any of the following ways.")
        add("")
        addAll(paymentMethodLines(channels, academicYear, DirectDebitOffer.SETTLES_THIS_ASK))
        add("")
        add("Kind regards,")
        add(SIGN_OFF)
    }.joinToString("\n")

    return EmailContent(
        recipientEmail = recipient.email,
        recipientName = recipient.fullName,
        subject = "Welcome to Blueshell Esports",
        markdownContent = markdownContent,
        senderNameOverride = SIGN_OFF,
        replyToOverride = REPLY_TO,
    )
}
