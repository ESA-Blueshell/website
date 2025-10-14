package net.blueshell.api.job.contact;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.model.User;
import net.blueshell.api.service.UserService;
import net.blueshell.api.service.ContactService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class SyncContactJob {

    private static final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> processingJobs = new ConcurrentHashMap<>();
    @Autowired
    private ContactService contacts;
    @Autowired
    private UserService users;

    @Async
    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public CompletableFuture<Void> sync(Long userId) {
        String jobKey = generateJobKey(userId);

        // Ensure uniqueness - prevent duplicate job execution
        if (processingJobs.putIfAbsent(jobKey, true) != null) {
            log.info("Contact sync job already processing for user ID: {}", userId);
            return CompletableFuture.completedFuture(null);
        }

        ReentrantLock lock = locks.computeIfAbsent(userId, k -> new ReentrantLock());

        try {
            lock.lock();
            log.info("Processing contact sync job for user ID: {}", userId);

            User user = users.findById(userId);

            // Perform the contact synchronization
            contacts.sync(user);

            // Update the user in the database with the potentially new contactId
            users.update(user);

            log.info("Successfully synchronized contact for user: {} (ID: {})", user.getEmail(), userId);

        } catch (RestClientResponseException e) {
            log.error("Failed to sync contact for user ID: {} due to REST client error: {}", userId, e.getMessage(), e);
            throw e; // Re-throw to trigger retry mechanism
        } catch (Exception e) {
            log.error("Failed to sync contact for user ID: {} due to unexpected error: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to sync contact", e);
        } finally {
            lock.unlock();
            processingJobs.remove(jobKey);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Recover
    public CompletableFuture<Void> recoverSyncContact(Exception ex, Long userId) {
        String jobKey = generateJobKey(userId);
        processingJobs.remove(jobKey);
        log.error("Failed to sync contact after retries for user ID: {}. Error: {}", userId, ex.getMessage(), ex);
        return CompletableFuture.completedFuture(null);
    }

    private String generateJobKey(Long userId) {
        return String.format("contact_sync_%d_%d", userId, System.currentTimeMillis() / 10000);
    }
}