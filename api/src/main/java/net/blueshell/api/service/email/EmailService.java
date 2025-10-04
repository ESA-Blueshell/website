package net.blueshell.api.service.email;

import net.blueshell.api.base.BaseEmail;
import net.blueshell.api.base.EmailContent;
import net.blueshell.api.email.*;
import net.blueshell.api.job.SendEmailJob;
import net.blueshell.api.model.User;
import net.blueshell.api.model.contribution.Contribution;
import net.blueshell.api.model.event.EventSignUp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmailService {

    @Autowired
    private SendEmailJob sendEmailJob;

    @Autowired
    private EmailTemplateService templateService;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Value("${app.url}")
    private String appUrl;

    public void activation(User user) {
        BaseEmail email = switch (user.getResetType()) {
            case MEMBER_ACTIVATION -> new MemberActivationEmail(user, frontendUrl, appUrl);
            case USER_ACTIVATION -> new UserActivationEmail(user, frontendUrl, appUrl);
            case null, default -> null;
        };

        if (email == null) {
            return;
        }

        scheduleEmail(email);
    }

    public void passwordReset(User user) {
        var email = new PasswordResetEmail(
                user,
                frontendUrl,
                appUrl
        );

        scheduleEmail(email);
    }

    public void contributionReminder(Contribution contribution) {
        var email = new ContributionReminderEmail(
                contribution.getUser(),
                frontendUrl,
                appUrl,
                contribution.getContributionPeriod()
        );

        scheduleEmail(email);
    }

    public void contributionReminders(List<Contribution> contributions) {
        for (var contribution : contributions) {
            contributionReminder(contribution);
        }
    }

    public void eventSignUp(EventSignUp eventSignUp) {
        var email = new EventSignupEmail(
                eventSignUp,
                frontendUrl,
                appUrl
        );

        scheduleEmail(email);
    }

    /**
     * Schedule any email using the BaseEmail abstraction to be sent asynchronously
     */
    private void scheduleEmail(BaseEmail email) {
        EmailContent content = email.buildEmailContent();

        String htmlContent = templateService.createEmail(
                email.getRecipient(),
                content.subject(),
                content.markdownContent()
        );

        // Schedule the email to be sent asynchronously via SendEmailJob
        sendEmailJob.sendHtmlEmail(
                content.recipient().getEmail(),
                content.subject(),
                htmlContent,
                content.senderName(),
                content.senderAddress()
        );
    }
}