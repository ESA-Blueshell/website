package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.user.persistence.User
import java.time.LocalDate

/**
 * The direct-debit pre-notification: what will be taken, and on what date.
 *
 * It asks for nothing. A member on direct debit who is also sent a payment request pays twice,
 * which is why these are two emails rather than one with a conditional paragraph. The amount is
 * passed in rather than priced from [feeType]: a sent pre-notification records
 * what it said would be taken, and this quotes that.
 */
fun createIncassoNotificationEmail(
    recipient: User,
    contributionPeriod: ContributionPeriod,
    feeType: BulkFeeType,
    amount: Double,
    debitDate: LocalDate,
): EmailContent {
    val academicYear = academicYearLabel(contributionPeriod)
    val debitDateText = formatDate(debitDate)
    val markdownContent = buildList {
        add("Dear ${recipient.fullName},")
        add("")
        add(
            "Your contribution for your $academicYear membership of ESA Blueshell will be collected " +
                "from your bank account on or around **$debitDateText**. Please make sure there are sufficient " +
                "funds in your account on that date.",
        )
        add("")
        add("**Amount to be collected: €${formatEuros(amount)}** (${feeReason(feeType)})")
        add("")
        add(
            "You do not need to transfer anything yourself. If you wish to end your membership, " +
                "reply to this email before $debitDateText so we can take you off the direct-debit list.",
        )
        add("")
        add("Kind regards,")
        add(SIGN_OFF)
    }.joinToString("\n")

    return EmailContent(
        recipientEmail = recipient.email,
        recipientName = recipient.fullName,
        subject = "Your Blueshell contribution will be collected automatically ($academicYear)",
        markdownContent = markdownContent,
        senderNameOverride = SIGN_OFF,
        replyToOverride = REPLY_TO,
    )
}
