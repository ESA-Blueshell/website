package net.blueshell.api.service.email;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseEmail;
import net.blueshell.api.base.EmailContent;
import net.blueshell.api.email.*;
import net.blueshell.api.model.User;
import net.blueshell.api.model.contribution.ContributionReminder;
import net.blueshell.api.model.event.EventSignUp;
import net.blueshell.api.service.UserService;
import net.blueshell.api.service.contribution.ContributionReminderService;
import net.blueshell.api.service.event.EventSignUpService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    private final EmailTemplateService templateService;
    private final EmailDeliveryService mailDelivery;
    private final UserService users;
    private final ContributionReminderService reminders;
    private final EventSignUpService eventSignUps;
    private final String frontendUrl;
    private final String appUrl;

    public EmailService(EmailTemplateService templateService,
                        EmailDeliveryService mailDelivery,
                        UserService users,
                        ContributionReminderService reminders,
                        EventSignUpService eventSignUps,
                        @Value("${frontend.url}") String frontendUrl,
                        @Value("${app.url}") String appUrl) {

        this.templateService = templateService;
        this.mailDelivery = mailDelivery;
        this.users = users;
        this.reminders = reminders;
        this.eventSignUps = eventSignUps;
        this.frontendUrl = frontendUrl;
        this.appUrl = appUrl;
    }

    public void sendContributionReminderEmail(Long reminderId) {
        ContributionReminder reminder = reminders.findById(reminderId);
        if (reminder == null || reminder.getUser() == null) return;

        BaseEmail email = new ContributionReminderEmail(
                reminder.getUser(),
                frontendUrl,
                appUrl,
                reminder.getContributionPeriod()
        );
        deliver(email);
    }

    public void sendEventSignupEmail(Long eventSignUpId) {
        EventSignUp eventSignUp = eventSignUps.findById(eventSignUpId);
        if (eventSignUp == null) return;

        BaseEmail email = new EventSignupEmail(eventSignUp, frontendUrl, appUrl);
        deliver(email);
    }

    /**
     * Render via template service and send via delivery service
     */
    private void deliver(BaseEmail email) {
        EmailContent content = email.buildEmailContent();

        String htmlContent = templateService.createEmail(
                content.recipient(),
                content.subject(),
                content.markdownContent()
        );

        mailDelivery.sendHtmlEmail(
                content.recipient().getEmail(),
                content.subject(),
                htmlContent,
                content.senderName(),
                content.senderAddress()
        );
    }

    public void sendUserResetEmail(Long userId) {
        User user = users.findById(userId);
        if (user == null || user.getResetType() == null) {
            log.info("Activation skipped: user={} or resetType missing", userId);
            return;
        } else {
            log.info("Sending {} email for user={}", user.getResetType(), userId);
        }

        BaseEmail email = switch (user.getResetType()) {
            case MEMBER_ACTIVATION -> new MemberActivationEmail(user, frontendUrl, appUrl);
            case USER_ACTIVATION -> new UserActivationEmail(user, frontendUrl, appUrl);
            case PASSWORD_RESET -> new PasswordResetEmail(user, frontendUrl, appUrl);
            default -> null;
        };
        if (email == null) return;

        deliver(email);
    }
}
