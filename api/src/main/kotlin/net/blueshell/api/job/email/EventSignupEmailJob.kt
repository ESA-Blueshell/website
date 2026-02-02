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
class EventSignupEmailJob {
    private val emails: EmailService? = null

    @Async
    @Retryable(retryFor = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 2000, multiplier = 2))
    fun send(eventSignUpId: Long?): CompletableFuture<Void?> {
        val key = jobKey("event_signup", eventSignUpId)
        if (processing.putIfAbsent(key, true) != null) {
            EventSignupEmailJob.log.info("Event signup email already processing for eventSignUpId={}", eventSignUpId)
            return CompletableFuture.completedFuture<Void?>(null)
        }
        try {
            emails!!.sendEventSignupEmail(eventSignUpId)
            return CompletableFuture.completedFuture<Void?>(null)
        } finally {
            processing.remove(key)
        }
    }

    @Recover
    fun recover(ex: Exception, eventSignUpId: Long?): CompletableFuture<Void?> {
        processing.remove(jobKey("event_signup", eventSignUpId))
        EventSignupEmailJob.log.error(
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
        private val processing = ConcurrentHashMap<String?, Boolean?>()
    }
}
