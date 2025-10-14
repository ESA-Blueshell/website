package net.blueshell.api.service.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
@Slf4j
public class EmailDeliveryService {

    @Autowired
    private JavaMailSender mailSender;

    /**
     * Sends an HTML email (synchronous). Jobs wrap retries/async.
     */
    public void sendHtmlEmail(String toEmail,
                              String subject,
                              String htmlContent,
                              String senderName,
                              String senderAddress) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderAddress, senderName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            // keep your inline assets
            helper.addInline("logo", new ClassPathResource("templates/assets/BSLOGO.png"), "image/png");
            helper.addInline("bg", new ClassPathResource("templates/assets/BackdropBlack.jpg"), "image/jpeg");

            mailSender.send(message);
            log.info("Sent email to {} with subject {}", toEmail, subject);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send email to {} with subject {}: {}", toEmail, subject, e.getMessage(), e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
}

