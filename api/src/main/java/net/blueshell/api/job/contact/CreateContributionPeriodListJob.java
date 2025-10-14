package net.blueshell.api.job.contact;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.model.contribution.ContributionPeriod;
import net.blueshell.api.service.ContactService;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class CreateContributionPeriodListJob {

    private static final ConcurrentHashMap<String, Boolean> processingJobs = new ConcurrentHashMap<>();

    private final ContactService contacts;
    private final ContributionPeriodService contributionPeriods;

    @Async
    @Retryable(retryFor = {Exception.class}, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public CompletableFuture<Void> createList(Long periodId) {
        String jobKey = generateJobKey(periodId);
        if (processingJobs.putIfAbsent(jobKey, true) != null) {
            log.info("Create-list job already processing for periodId={}", periodId);
            return CompletableFuture.completedFuture(null);
        }

        try {
            ContributionPeriod period = contributionPeriods.findById(periodId);
            if (period.getListId() != null) {
                log.info("List already exists (id={}) for periodId={}", period.getListId(), periodId);
                return CompletableFuture.completedFuture(null);
            }

            Long listId = contacts.createList(period);
            period.setListId(listId);
            contributionPeriods.update(period);
            log.info("Created list {} for periodId={}", listId, periodId);

            return CompletableFuture.completedFuture(null);
        } catch (RestClientResponseException e) {
            log.error("Brevo error creating list for periodId={}: {}", periodId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error creating list for periodId={}: {}", periodId, e.getMessage(), e);
            throw new RuntimeException("Failed to create list", e);
        } finally {
            processingJobs.remove(jobKey);
        }
    }

    @Recover
    public CompletableFuture<Void> recover(Exception ex, Long periodId) {
        processingJobs.remove(generateJobKey(periodId));
        log.error("Giving up creating list for periodId={}: {}", periodId, ex.getMessage(), ex);
        return CompletableFuture.completedFuture(null);
    }

    private String generateJobKey(Long periodId) {
        return String.format("create_list_for_period_%d_at_%d", periodId, System.currentTimeMillis() / 10000);
    }
}
