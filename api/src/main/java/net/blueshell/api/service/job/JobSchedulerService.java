package net.blueshell.api.service.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.job.EmailJob;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service to schedule and execute jobs
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobSchedulerService {
    
    private final JobExecutionService jobExecutionService;
    private final ApplicationContext applicationContext;
    
    /**
     * Schedule an email notification job
     */
    public void scheduleEmailNotification(String to, String subject, String content) {
        EmailJob job = applicationContext.getBean(EmailJob.class);
        job.setTo(to);
        job.setSubject(subject);
        job.setContent(content);
        
        Map<String, Object> parameters = Map.of(
                "to", to,
                "subject", subject,
                "content", content != null ? content : ""
        );
        
        jobExecutionService.executeJob(job, parameters);
    }
    
    /**
     * Schedule an email notification job with template
     */
    public void scheduleTemplatedEmailNotification(String to, String subject, String template) {
        EmailJob job = applicationContext.getBean(EmailJob.class);
        job.setTo(to);
        job.setSubject(subject);
        job.setTemplate(template);
        
        Map<String, Object> parameters = Map.of(
                "to", to,
                "subject", subject,
                "template", template
        );
        
        jobExecutionService.executeJob(job, parameters);
    }
}