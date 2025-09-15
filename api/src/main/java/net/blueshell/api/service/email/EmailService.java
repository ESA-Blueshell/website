package net.blueshell.api.service.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.email.PasswordResetEmail;
import net.blueshell.api.model.User;
import net.blueshell.api.base.BaseEmail;
import net.blueshell.api.base.EmailContent;
import net.blueshell.api.email.MemberActivationEmail;
import net.blueshell.api.email.UserActivationEmail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

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
            case PASSWORD_RESET -> new PasswordResetEmail(user, frontendUrl, appUrl);
        };

        sendEmail(email);
    }

    /**
     * Send any email using the BaseEmail abstraction
     */
    private void sendEmail(BaseEmail email) {
        EmailContent content = email.buildEmailContent();

        String htmlContent = templateService.createEmail(
                email.getRecipient(),
                content.subject(),
                content.markdownContent()
        );

        sendHtmlEmail(
                content.recipient().getEmail(),
                content.subject(),
                htmlContent,
                content.senderName(),
                content.senderAddress()
        );
    }

    private void sendHtmlEmail(String toEmail, String subject, String htmlContent, String senderName, String senderAddress) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderAddress, senderName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true = HTML content

            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }
}