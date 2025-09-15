
package net.blueshell.api.job;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SendEmailJob {

    @Autowired
    private JavaMailSender mailSender;

    private static final ConcurrentHashMap<String, Boolean> processingJobs = new ConcurrentHashMap<>();

    @Async
    @Retryable(
            value = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void sendHtmlEmail(String toEmail, String subject, String htmlContent, String senderName, String senderAddress) {
        String jobKey = generateJobKey(toEmail, subject);

        // Ensure uniqueness - prevent duplicate job execution
        if (processingJobs.putIfAbsent(jobKey, true) != null) {
            log.info("Email job already processing for recipient: {} with subject: {}", toEmail, subject);
            CompletableFuture.completedFuture(null);
            return;
        }

        try {
            log.info("Processing email job for recipient: {} with subject: {}", toEmail, subject);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderAddress, senderName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true = HTML content

            helper.addInline("logo",
                    new org.springframework.core.io.ClassPathResource("templates/assets/BSLOGO.png"),
                    "image/png");

            helper.addInline("bg",
                    new org.springframework.core.io.ClassPathResource("templates/assets/BackdropBlack.jpg"),
                    "image/jpeg");


            mailSender.send(message);

            log.info("Successfully sent email to: {} with subject: {}", toEmail, subject);

        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send email to: {} with subject: {}. Error: {}", toEmail, subject, e.getMessage(), e);
            throw new RuntimeException("Failed to send email", e);
        } finally {
            processingJobs.remove(jobKey);
        }

        CompletableFuture.completedFuture(null);
    }

    @Recover
    public CompletableFuture<Void> recover(Exception ex, String toEmail, String subject, String htmlContent, String senderName, String senderAddress) {
        String jobKey = generateJobKey(toEmail, subject);
        processingJobs.remove(jobKey);
        log.error("Failed to send email after retries. Recipient: {}, Subject: {}, Error: {}",
                toEmail, subject, ex.getMessage(), ex);
        return CompletableFuture.completedFuture(null);
    }

    private String generateJobKey(String toEmail, String subject) {
        return String.format("email_%s_%s_%d",
                toEmail,
                subject.hashCode(),
                System.currentTimeMillis() / 10000); // Group by 10-second windows
    }
}