package net.blueshell.api.service.brevo;

import net.blueshell.api.model.Contribution;
import net.blueshell.api.model.ContributionPeriod;
import net.blueshell.api.model.EventSignUp;
import net.blueshell.api.model.User;
import net.blueshell.api.repository.ContributionPeriodRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sendinblue.ApiClient;
import sendinblue.ApiException;
import sendinblue.Configuration;
import sendinblue.auth.ApiKeyAuth;
import sibApi.TransactionalEmailsApi;
import sibModel.SendSmtpEmail;
import sibModel.SendSmtpEmailTo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

@Service
public class EmailService {

    @Autowired
    ContributionPeriodRepository contributionPeriodRepository;
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

    @NotNull
    private static Properties getParams(ContributionPeriod contributionPeriod) {
        Properties params = new Properties();
        params.setProperty("startDate", contributionPeriod.getStartDate().toString());
        params.setProperty("endDate", contributionPeriod.getEndDate().toString());
        params.setProperty("halfYearFee", String.valueOf(contributionPeriod.getHalfYearFee()));
        params.setProperty("fullYearFee", String.valueOf(contributionPeriod.getFullYearFee()));
        params.setProperty("alumniFee", String.valueOf(contributionPeriod.getAlumniFee()));
        return params;
    }

    public void sendUserActivationEmail(User user) throws ApiException {
        Properties params = new Properties();
        params.setProperty("link", String.format(this.frontendUrl + "/account/activate?username=%s&token=%s", user.getUsername(), user.getResetKey()));
        sendEmail(Collections.singletonList(user.getEmail()), this.userActivationTemplateId, params);
    }

    public void sendMemberActivationEmail(User user) throws ApiException {
        Properties params = new Properties();
        params.setProperty("link", String.format(this.frontendUrl + "/account/activate?token=%s", user.getResetKey()));
        sendEmail(Collections.singletonList(user.getEmail()), this.memberActivationTemplateId, params);
    }

    public void sendPasswordResetEmail(User user) throws ApiException {
        Properties params = new Properties();
        params.setProperty("link", String.format(this.frontendUrl + "/login/reset-password?username=%s&token=%s", user.getUsername(), user.getResetKey()));
        sendEmail(Collections.singletonList(user.getEmail()), this.passwordResetTemplateId, params);
    }

    public void sendEventSignUpEmail(EventSignUp signUp) throws ApiException {
        Properties params = new Properties();
        params.setProperty("link", String.format(this.frontendUrl + "/events/signups/edit/%s", signUp.getGuest().getAccessToken()));
        params.setProperty("eventTitle", signUp.getEvent().getTitle());
        sendEmail(Collections.singletonList(signUp.getGuest().getEmail()), this.eventSignupTemplateId, params);
    }

    public void sendContributionEmail(User user) throws ApiException {
        List<ContributionPeriod> contributionPeriods = contributionPeriodRepository.findCurrentOrLatestContributionPeriod();
        if (!contributionPeriods.isEmpty()) {
            ContributionPeriod contributionPeriod = contributionPeriods.get(0);
            Properties params = getParams(contributionPeriod);
            sendEmail(Collections.singletonList(user.getEmail()), this.contributionTemplateId, params);
        }
    }

    public void sendContributionReminderEmail(List<User> users, ContributionPeriod contributionPeriod) throws ApiException {
        Properties params = getParams(contributionPeriod);
        List<String> emails = users.stream().map(User::getEmail).collect(Collectors.toList());
        sendEmail(emails, this.contributionTemplateId, params);
    }

    private void sendEmail(List<String> toEmails, Long templateId, Properties params) throws ApiException {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        ApiKeyAuth apiKey = (ApiKeyAuth) defaultClient.getAuthentication("api-key");
        apiKey.setApiKey(this.apiKey);

        TransactionalEmailsApi api = new TransactionalEmailsApi();
        SendSmtpEmail sendSmtpEmail = new SendSmtpEmail();

        List<SendSmtpEmailTo> toList = new ArrayList<>();
        for (String toEmail : toEmails) {
            SendSmtpEmailTo to = new SendSmtpEmailTo();
            to.setEmail(toEmail);
            toList.add(to);
        }

        sendSmtpEmail.to(toList);
        sendSmtpEmail.setParams(params);
        sendSmtpEmail.setTemplateId(templateId);

        api.sendTransacEmail(sendSmtpEmail);
    }

    public void sendContributionReminders(List<Contribution> contributions) {
    }
}
