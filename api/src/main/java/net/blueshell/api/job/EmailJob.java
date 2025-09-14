package net.blueshell.api.job;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.Job;
import net.blueshell.api.service.email.SmtpEmailService;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class EmailJob implements Job {

    private final SmtpEmailService emailService;

    @Setter
    private String to;
    @Setter
    private String subject;
    @Setter
    private String content;
    @Setter
    private String template;

    @Override
    public void execute() throws Exception {
        log.info("Executing email notification to: {}", to);

        if (template != null) {
            // Send templated email - you'd need to implement this in EmailService
            // emailService.sendTemplatedEmail(to, subject, template, templateParams);
            log.info("Would send templated email using template: {}", template);
        } else {
            // Send simple email - you'd need to implement this in EmailService
            // emailService.sendSimpleEmail(to, subject, content);
            log.info("Would send simple email with content length: {}",
                    content != null ? content.length() : 0);
        }

        // Simulate some work
        Thread.sleep(2000);

        log.info("Email notification completed");
    }
}