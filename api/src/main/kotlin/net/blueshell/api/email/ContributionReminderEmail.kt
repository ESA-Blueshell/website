package net.blueshell.api.email

import net.blueshell.api.base.BaseEmail
import net.blueshell.api.model.User
import net.blueshell.api.model.contribution.ContributionPeriod

class ContributionReminderEmail(
    recipient: User,
    frontendUrl: String,
    appUrl: String,
    private val contributionPeriod: ContributionPeriod
) : BaseEmail(recipient, frontendUrl, appUrl) {
    override val subject: String
        get() = "Contribution Payment Reminder - Blueshell Esports"

    override val markdownContent: String
        get() = String.format(
            """
                        Dear %s,
                        
                        This is a friendly reminder that your contribution payment for the period %s to %s is due.
                        
                        Payment options:
                        - Half year fee: €%.2f
                        - Full year fee: €%.2f
                        - Alumni fee: €%.2f
                        
                        Please make your payment at your earliest convenience via our [website](%s).
                        
                        If you have already made your payment, please disregard this message.
                        
                        Kind regards,
                        Treasurer of Blueshell Esports
                        
                        """.trimIndent(),
            recipient.fullName,
            contributionPeriod.startDate,
            contributionPeriod.endDate,
            contributionPeriod.halfYearFee,
            contributionPeriod.fullYearFee,
            contributionPeriod.alumniFee,
            appUrl
        )

    override val senderName: String
        get() = "Treasurer of Blueshell"

    override val replyTo: String
        get() = "board@blueshell.utwente.nl"
}
