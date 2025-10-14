package net.blueshell.api.job.email;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.service.email.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ContributionReminderEmailJob {

    private static final ConcurrentHashMap<String, Boolean> processing = new ConcurrentHashMap<>();
    @Autowired
    private EmailService emails;

    @Async
    @Retryable(retryFor = {Exception.class}, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public CompletableFuture<Void> send(Long reminderId) {
        String key = jobKey("contribution_reminder", reminderId);
        if (processing.putIfAbsent(key, true) != null) {
            log.info("Contribution reminder already processing for reminderId={}", reminderId);
            return CompletableFuture.completedFuture(null);
        }
        try {
            emails.sendContributionReminderEmail(reminderId);
            return CompletableFuture.completedFuture(null);
        } finally {
            processing.remove(key);
        }
    }

    @Recover
    public CompletableFuture<Void> recover(Exception ex, Long reminderId) {
        processing.remove(jobKey("contribution_reminder", reminderId));
        log.error("Giving up contribution reminder for reminderId={}: {}", reminderId, ex.getMessage(), ex);
        return CompletableFuture.completedFuture(null);
    }

    private String jobKey(String type, Long id) {
        return "%s_%d_%d".formatted(type, id, System.currentTimeMillis() / 10000);
    }
}
