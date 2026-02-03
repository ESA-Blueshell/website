package net.blueshell.api.job.email

import net.blueshell.api.service.email.EmailService
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Service
class EventSignupEmailJob(
    private val emails: EmailService
) {
    @Async
    @Retryable(retryFor = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 2000, multiplier = 2))
    fun send(eventSignUpId: Long?): CompletableFuture<Void?> {
        val key = jobKey("event_signup", eventSignUpId)
        if (processing.putIfAbsent(key, true) != null) {
            log.info("Event signup email already processing for eventSignUpId={}", eventSignUpId)
            return CompletableFuture.completedFuture<Void?>(null)
        }
        try {
            emails.sendEventSignupEmail(eventSignUpId)
            return CompletableFuture.completedFuture<Void?>(null)
        } finally {
            processing.remove(key)
        }
    }

    @Recover
    fun recover(ex: Exception, eventSignUpId: Long?): CompletableFuture<Void?> {
        processing.remove(jobKey("event_signup", eventSignUpId))
        log.error(
            "Giving up event signup email for eventSignUpId={}: {}",
            eventSignUpId,
            ex.message,
            ex
        )
        return CompletableFuture.completedFuture<Void?>(null)
    }

    private fun jobKey(type: String?, id: Long?): String {
        return "%s_%d_%d".formatted(type, id, System.currentTimeMillis() / 10000)
    }

    companion object {
        private val log = LoggerFactory.getLogger(EventSignupEmailJob::class.java)
        private val processing = ConcurrentHashMap<String?, Boolean?>()
    }
}
