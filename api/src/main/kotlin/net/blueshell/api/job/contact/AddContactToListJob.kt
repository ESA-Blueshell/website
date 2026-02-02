package net.blueshell.api.job.contact;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.model.User;
import net.blueshell.api.model.contribution.ContributionPeriod;
import net.blueshell.api.service.ContactService;
import net.blueshell.api.service.UserService;
import net.blueshell.api.service.contribution.ContributionPeriodService;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class AddContactToListJob {

    private static final ConcurrentHashMap<String, Boolean> processingJobs = new ConcurrentHashMap<>();

    private final ContactService contacts;
    private final UserService users;
    private final ContributionPeriodService contributionPeriods;

    @Async
    @Retryable(retryFor = {Exception.class}, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public CompletableFuture<Void> addToList(Long userId, Long periodId) {
        String jobKey = generateJobKey(userId, periodId);
        if (processingJobs.putIfAbsent(jobKey, true) != null) {
            log.info("Add-to-list job already processing for userId={} periodId={}", userId, periodId);
            return CompletableFuture.completedFuture(null);
        }

        try {
            User user = users.findById(userId);
            ContributionPeriod period = contributionPeriods.findById(periodId);

            // Ensure list exists (period listener also creates it; this is a safety net).
            if (period.getListId() == null) {
                var listId = contacts.createList(period);
                contributionPeriods.updateListId(periodId, listId);
                log.info("Created list {} for periodId={}", listId, periodId);
            }

            contacts.addToList(period, user);
            log.info("Added user {} (id={}) to list {} for periodId={}",
                    user.getEmail(), userId, period.getListId(), periodId);

            return CompletableFuture.completedFuture(null);
        } catch (RestClientResponseException e) {
            log.error("Brevo error adding userId={} to periodId={}: {}", userId, periodId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error adding userId={} to periodId={}: {}", userId, periodId, e.getMessage(), e);
            throw new RuntimeException("Failed to add contact to list", e);
        } finally {
            processingJobs.remove(jobKey);
        }
    }

    @Recover
    public CompletableFuture<Void> recover(Exception ex, Long userId, Long periodId) {
        processingJobs.remove(generateJobKey(userId, periodId));
        log.error("Giving up adding userId={} to periodId={}: {}", userId, periodId, ex.getMessage(), ex);
        return CompletableFuture.completedFuture(null);
    }

    private String generateJobKey(Long userId, Long periodId) {
        return String.format("add_contact_user_%d_to_period_%d_at_%d",
                userId, periodId, System.currentTimeMillis() / 10000);
    }
}
