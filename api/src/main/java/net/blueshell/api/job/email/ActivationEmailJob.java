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
public class ActivationEmailJob {

    private static final ConcurrentHashMap<String, Boolean> processing = new ConcurrentHashMap<>();

    @Autowired private EmailService emails;

    @Async
    @Retryable(retryFor = {Exception.class}, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public CompletableFuture<Void> send(Long userId) {
        String key = jobKey("activation", userId);
        if (processing.putIfAbsent(key, true) != null) {
            log.info("Activation email already processing for userId={}", userId);
            return CompletableFuture.completedFuture(null);
        }

        try {
            emails.sendActivationEmail(userId);
            return CompletableFuture.completedFuture(null);
        } finally {
            processing.remove(key);
        }
    }

    @Recover
    public CompletableFuture<Void> recover(Exception ex, Long userId) {
        processing.remove(jobKey("activation", userId));
        log.error("Giving up activation email for userId={}: {}", userId, ex.getMessage(), ex);
        return CompletableFuture.completedFuture(null);
    }

    private String jobKey(String type, Long id) {
        return "%s_%d_%d".formatted(type, id, System.currentTimeMillis() / 10000);
    }
}
