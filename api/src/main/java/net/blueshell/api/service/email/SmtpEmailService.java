package net.blueshell.api.service.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import net.blueshell.api.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

@Service
public class SmtpEmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Value("${app.url}")
    private String appUrl;

    public void sendUserActivationEmail(User user) {
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("user", user);
        templateParams.put("activationLink", String.format(this.frontendUrl + "/account/activate?username=%s&token=%s", user.getUsername(), user.getResetKey()));
        templateParams.put("appUrl", this.appUrl);

        String htmlContent = emailTemplateService.processTemplate("emails/user-activation", templateParams);

        sendHtmlEmail(user.getEmail(), "Activate your Blueshell Account", htmlContent, "ESA Blueshell", "sitecie@blueshell.utwente.nl");
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