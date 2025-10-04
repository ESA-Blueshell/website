package net.blueshell.api.email;

import net.blueshell.api.base.BaseEmail;
import net.blueshell.api.model.User;
import net.blueshell.api.model.contribution.ContributionPeriod;

public class ContributionReminderEmail extends BaseEmail {

    private final ContributionPeriod contributionPeriod;

    public ContributionReminderEmail(
            User recipient,
            String frontendUrl,
            String appUrl,
            ContributionPeriod contributionPeriod
    ) {
        super(recipient, frontendUrl, appUrl);
        this.contributionPeriod = contributionPeriod;
    }

    @Override
    public String getSubject() {
        return "Contribution Payment Reminder - Blueshell Esports";
    }

    @Override
    public String getMarkdownContent() {
        return String.format(
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
                        Blueshell Esports Treasury
                        """,
                recipient.getFullName(),
                contributionPeriod.getStartDate(),
                contributionPeriod.getEndDate(),
                contributionPeriod.getHalfYearFee(),
                contributionPeriod.getFullYearFee(),
                contributionPeriod.getAlumniFee(),
                appUrl
        );
    }

    @Override
    public String getSenderName() {
        return "Blueshell Treasurer";
    }

    @Override
    public String getSenderAddress() {
        return "treasurer@blueshell.utwente.nl";
    }
}