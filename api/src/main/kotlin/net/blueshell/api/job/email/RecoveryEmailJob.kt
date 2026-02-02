package net.blueshell.api.job.email

import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import net.blueshell.api.common.enums.ResetType
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
class RecoveryEmailJob {
    private val emails: EmailService? = null

    @Async
    @Retryable(retryFor = [Exception::class], maxAttempts = 3, backoff = Backoff(delay = 2000, multiplier = 2))
    fun send(userId: Long?, token: String?, resetType: ResetType): CompletableFuture<Void?> {
        val key = jobKey(resetType.toString(), userId)
        if (processing.putIfAbsent(key, true) != null) {
            RecoveryEmailJob.log.info("Reset email already processing for userId={}", userId)
            return CompletableFuture.completedFuture<Void?>(null)
        }

        try {
            emails!!.sendUserResetEmail(userId, token, resetType)
            return CompletableFuture.completedFuture<Void?>(null)
        } finally {
            processing.remove(key)
        }
    }

    @Recover
    fun recover(ex: Exception, userId: Long?): CompletableFuture<Void?> {
        processing.remove(jobKey("reset", userId))
        RecoveryEmailJob.log.error("Giving up reset email for userId={}: {}", userId, ex.message, ex)
        return CompletableFuture.completedFuture<Void?>(null)
    }

    private fun jobKey(type: String?, id: Long?): String {
        return "%s_%d_%d".formatted(type, id, System.currentTimeMillis() / 10000)
    }

    companion object {
        private val processing = ConcurrentHashMap<String?, Boolean?>()
    }
}
