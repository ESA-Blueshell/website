
package net.blueshell.api.service.brevo;

import net.blueshell.api.model.Contribution;
import net.blueshell.api.model.ContributionPeriod;
import net.blueshell.api.model.EventSignUp;
import net.blueshell.api.model.User;
import net.blueshell.api.repository.ContributionPeriodRepository;
import net.blueshell.api.service.email.EmailTemplateService;
import net.blueshell.api.service.email.SmtpEmailService;
import net.blueshell.clients.brevo.api.TransactionalEmailsApi;
import net.blueshell.clients.brevo.invoker.ApiClient;
import net.blueshell.clients.brevo.model.SendSmtpEmail;
import net.blueshell.clients.brevo.model.SendSmtpEmailToInner;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EmailService {

    @Autowired
    ContributionPeriodRepository contributionPeriodRepository;

    private final TransactionalEmailsApi transactionalEmailsApi;
    private final SmtpEmailService smtpEmailService;

    @Value("${brevo.apiKey}")
    private String apiKey;
    @Value("${brevo.templates.userActivationId}")
    private Long userActivationTemplateId;
    @Value("${brevo.templates.memberActivationId}")
    private Long memberActivationTemplateId;
    @Value("${brevo.templates.eventSignupId}")
    private Long eventSignupTemplateId;
    @Value("${brevo.templates.passwordResetId}")
    private Long passwordResetTemplateId;
    @Value("${brevo.templates.contributionId}")
    private Long contributionTemplateId;
    @Value("${brevo.templates.contributionReminderId}")
    private Long contributionReminderTemplateId;
    @Value("${frontend.url}")
    private String frontendUrl;

    public EmailService(SmtpEmailService smtpEmailService) {
        this.smtpEmailService = smtpEmailService;
        ApiClient apiClient = new ApiClient();
        this.transactionalEmailsApi = new TransactionalEmailsApi(apiClient);
    }

    @NotNull
    private static Map<String, Object> getParams(ContributionPeriod contributionPeriod) {
        Map<String, Object> params = new HashMap<>();
        params.put("startDate", contributionPeriod.getStartDate().toString());
        params.put("endDate", contributionPeriod.getEndDate().toString());
        params.put("halfYearFee", String.valueOf(contributionPeriod.getHalfYearFee()));
        params.put("fullYearFee", String.valueOf(contributionPeriod.getFullYearFee()));
        params.put("alumniFee", String.valueOf(contributionPeriod.getAlumniFee()));
        return params;
    }

    public void sendUserActivationEmail(User user) {
        smtpEmailService.sendUserActivationEmail(user);
//        Map<String, Object> params = new HashMap<>();
//        params.put("link", String.format(this.frontendUrl + "/account/activate?username=%s&token=%s", user.getUsername(), user.getResetKey()));
//        sendEmail(Collections.singletonList(user.getEmail()), this.userActivationTemplateId, params);
    }

    public void sendMemberActivationEmail(User user) {
        Map<String, Object> params = new HashMap<>();
        params.put("link", String.format(this.frontendUrl + "/account/activate?token=%s", user.getResetKey()));
        sendEmail(Collections.singletonList(user.getEmail()), this.memberActivationTemplateId, params);
    }

    public void sendPasswordResetEmail(User user) {
        Map<String, Object> params = new HashMap<>();
        params.put("link", String.format(this.frontendUrl + "/login/reset-password?username=%s&token=%s", user.getUsername(), user.getResetKey()));
        sendEmail(Collections.singletonList(user.getEmail()), this.passwordResetTemplateId, params);
    }

    public void sendEventSignUpEmail(EventSignUp signUp) {
        Map<String, Object> params = new HashMap<>();
        params.put("link", String.format(this.frontendUrl + "/events/signups/edit/%s", signUp.getGuest().getAccessToken()));
        params.put("eventTitle", signUp.getEvent().getTitle());
        sendEmail(Collections.singletonList(signUp.getGuest().getEmail()), this.eventSignupTemplateId, params);
    }

    public void sendContributionEmail(User user) {
        List<ContributionPeriod> contributionPeriods = contributionPeriodRepository.findCurrentOrLatestContributionPeriod();
        if (!contributionPeriods.isEmpty()) {
            ContributionPeriod contributionPeriod = contributionPeriods.getFirst();
            Map<String, Object> params = getParams(contributionPeriod);
            sendEmail(Collections.singletonList(user.getEmail()), this.contributionTemplateId, params);
        }
    }

    public void sendContributionReminderEmail(List<User> users, ContributionPeriod contributionPeriod) {
        Map<String, Object> params = getParams(contributionPeriod);
        List<String> emails = users.stream().map(User::getEmail).collect(Collectors.toList());
        sendEmail(emails, this.contributionReminderTemplateId, params);
    }

    private void sendEmail(List<String> toEmails, Long templateId, Map<String, Object> params) {
        // Configure API client with authentication
        ApiClient apiClient = transactionalEmailsApi.getApiClient();
        apiClient.addDefaultHeader("api-key", this.apiKey);

        // Create the email request
        SendSmtpEmail sendSmtpEmail = new SendSmtpEmail();

        // Set recipients
        List<SendSmtpEmailToInner> toList = new ArrayList<>();
        for (String toEmail : toEmails) {
            SendSmtpEmailToInner to = new SendSmtpEmailToInner();
            to.setEmail(toEmail);
            toList.add(to);
        }
        sendSmtpEmail.setTo(toList);

        // Set template and parameters
        sendSmtpEmail.setTemplateId(templateId);
        sendSmtpEmail.setParams(params);

        // Send the email
        transactionalEmailsApi.sendTransacEmail(sendSmtpEmail);
    }

    public void sendContributionReminders(List<Contribution> contributions, ContributionPeriod contributionPeriod) {
        // Implementation for sending contribution reminders
        // This method can be implemented based on your business requirements
        var users = new ArrayList<User>();
        contributions.forEach(contribution -> {
            users.add(contribution.getUser());
        });
        sendContributionReminderEmail(users, contributionPeriod);
    }
}
