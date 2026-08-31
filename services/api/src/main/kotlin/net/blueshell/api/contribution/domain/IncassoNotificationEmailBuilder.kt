package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.user.persistence.User
import java.time.LocalDate

/**
 * The direct-debit pre-notification: told before the money is taken, what will be taken
 * and on what date.
 *
 * It asks for nothing. A member on direct debit who is sent a payment request pays twice,
 * which is the reason the two statements are separate emails rather than one with a
 * conditional paragraph.
 *
 * Built as an [EmailContent] like every other email, which is what makes it previewable
 * through `EmailPreviewRenderer` without a second rendering path.
 */
fun createIncassoNotificationEmail(
    recipient: User,
    contributionPeriod: ContributionPeriod,
    feeType: BulkFeeType,
    debitDate: LocalDate,
): EmailContent {
    val academicYear = academicYearLabel(contributionPeriod)
    val amount = resolveFeeAmount(feeType, contributionPeriod)
    val when0 = formatDate(debitDate)
    val markdownContent = buildList {
        add("Dear ${recipient.fullName},")
        add("")
        add(
            "Your contribution for your $academicYear membership of ESA Blueshell will be collected " +
                "from your bank account on or around **$when0**. Please make sure there are sufficient " +
                "funds in your account on that date.",
        )
        add("")
        add("**Amount to be collected: €${formatEuros(amount)}** (${feeReason(feeType)})")
        add("")
        add(
            "You do not need to transfer anything yourself. If you wish to end your membership, " +
                "reply to this email before $when0 so we can take you off the direct-debit list.",
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
        replyToOverride = "board@blueshell.utwente.nl",
    )
}
