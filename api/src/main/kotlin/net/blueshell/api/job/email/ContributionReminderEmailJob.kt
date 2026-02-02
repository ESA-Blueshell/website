package net.blueshell.api.job.email

import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import net.blueshell.api.service.email.EmailService
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Service
@Slf4j
@RequiredArgsConstructor
class ContributionReminderEmailJob {
    private val emails: EmailService? = null

    @Async
    @Retryable(retryFor = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 2000, multiplier = 2))
    fun send(reminderId: Long?): CompletableFuture<Void?> {
        val key = jobKey("contribution_reminder", reminderId)
        if (processing.putIfAbsent(key, true) != null) {
            ContributionReminderEmailJob.log.info(
                "Contribution reminder already processing for reminderId={}",
                reminderId
            )
            return CompletableFuture.completedFuture<Void?>(null)
        }
        try {
            emails!!.sendContributionReminderEmail(reminderId)
            return CompletableFuture.completedFuture<Void?>(null)
        } finally {
            processing.remove(key)
        }
    }

    @Recover
    fun recover(ex: Exception, reminderId: Long?): CompletableFuture<Void?> {
        processing.remove(jobKey("contribution_reminder", reminderId))
        ContributionReminderEmailJob.log.error(
            "Giving up contribution reminder for reminderId={}: {}",
            reminderId,
            ex.message,
            ex
        )
        return CompletableFuture.completedFuture<Void?>(null)
    }

    private fun jobKey(type: String?, id: Long?): String {
        return "%s_%d_%d".formatted(type, id, System.currentTimeMillis() / 10000)
    }

    companion object {
        private val processing = ConcurrentHashMap<String?, Boolean?>()
    }
}
