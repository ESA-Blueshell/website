package net.blueshell.api.job.contact;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.model.User;
import net.blueshell.api.service.ContactService;
import net.blueshell.api.service.UserService;
import net.blueshell.api.service.contribution.ContributionPeriodService;
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
public class RemoveContactFromListJob {

    private static final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> processingJobs = new ConcurrentHashMap<>();
    @Autowired
    private ContactService contacts;
    @Autowired
    private UserService users;
    @Autowired
    private ContributionPeriodService contributionPeriods;

    @Async
    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public CompletableFuture<Void> removeFromList(Long userId, Long periodId) {
        String jobKey = generateJobKey(userId, periodId);

        // Ensure uniqueness - prevent duplicate job execution
        if (processingJobs.putIfAbsent(jobKey, true) != null) {
            log.info("Remove-from-list job already processing for user ID: {} and period ID: {}", userId, periodId);
            return CompletableFuture.completedFuture(null);
        }

        ReentrantLock lock = locks.computeIfAbsent(userId, k -> new ReentrantLock());

        try {
            lock.lock();
            log.info("Starting remove-from-list job for user ID: {} and period ID: {}", userId, periodId);

            User user = users.findById(userId);
            var period = contributionPeriods.findById(periodId);

            contacts.removeFromList(period, user);

            log.info("Successfully removed contact {} (user ID: {}) from list for period ID: {}",
                    user.getEmail(), userId, periodId);

        } catch (RestClientResponseException e) {
            log.error("Failed to remove contact for user ID: {} and period ID: {} due to REST client error: {}",
                    userId, periodId, e.getMessage(), e);
            throw e; // Re-throw to trigger retry mechanism
        } catch (Exception e) {
            log.error("Failed to remove contact for user ID: {} and period ID: {} due to unexpected error: {}",
                    userId, periodId, e.getMessage(), e);
            throw new RuntimeException("Failed to remove contact from list", e);
        } finally {
            lock.unlock();
            processingJobs.remove(jobKey);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Recover
    public CompletableFuture<Void> recoverRemoveContactFromList(Exception ex, Long userId, Long periodId) {
        String jobKey = generateJobKey(userId, periodId);
        processingJobs.remove(jobKey);
        log.error("Failed to remove user with ID: {} from contribution period list with ID: {}. Error: {}",
                userId, periodId, ex.getMessage(), ex);
        return CompletableFuture.completedFuture(null);
    }

    private String generateJobKey(Long userId, Long periodId) {
        // Bucket into ~10s windows to reduce duplicate overlap without starving retries
        return String.format("remove_contact_user_%d_from_period_%d_at_%d",
                userId, periodId, System.currentTimeMillis() / 10000);
        // Note: the time-bucketing behavior is preserved from the original implementation.
    }
}
