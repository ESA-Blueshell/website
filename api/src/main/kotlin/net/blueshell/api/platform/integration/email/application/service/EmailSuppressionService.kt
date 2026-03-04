package net.blueshell.api.platform.integration.email.application.service

import net.blueshell.api.platform.integration.email.persistence.EmailSuppression
import net.blueshell.api.platform.integration.email.persistence.SuppressionReason
import net.blueshell.api.platform.integration.email.persistence.repository.EmailSuppressionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class EmailSuppressionService(
    private val repository: EmailSuppressionRepository,
) {
    /**
     * Returns true if the email address is suppressed and should not receive emails.
     *
     * Hard bounces and complaints suppress immediately.
     * Soft bounces only suppress once the threshold is reached.
     */
    fun isSuppressed(email: String): Boolean {
        val suppression = repository.findByEmail(email.lowercase()) ?: return false
        return when (suppression.reason) {
            SuppressionReason.HARD_BOUNCE, SuppressionReason.COMPLAINT -> true
            SuppressionReason.SOFT_BOUNCE_THRESHOLD -> suppression.bounceCount >= SOFT_BOUNCE_THRESHOLD
        }
    }

    /**
     * Immediately suppress an address after a hard bounce.
     */
    @Transactional
    fun suppressHardBounce(email: String) {
        val normalized = email.lowercase()
        val existing = repository.findByEmail(normalized)
        if (existing != null) {
            existing.lastSeenAt = Instant.now()
            existing.bounceCount += 1
            existing.reason = SuppressionReason.HARD_BOUNCE
            repository.save(existing)
        } else {
            repository.save(
                EmailSuppression(
                    email = normalized,
                    reason = SuppressionReason.HARD_BOUNCE,
                )
            )
        }
        log.info("Suppressed email (hard bounce): {}", normalized)
    }

    /**
     * Immediately suppress an address after a spam complaint.
     */
    @Transactional
    fun suppressComplaint(email: String) {
        val normalized = email.lowercase()
        val existing = repository.findByEmail(normalized)
        if (existing != null) {
            existing.lastSeenAt = Instant.now()
            existing.reason = SuppressionReason.COMPLAINT
            repository.save(existing)
        } else {
            repository.save(
                EmailSuppression(
                    email = normalized,
                    reason = SuppressionReason.COMPLAINT,
                )
            )
        }
        log.info("Suppressed email (complaint): {}", normalized)
    }

    /**
     * Record a soft bounce. Suppresses after [SOFT_BOUNCE_THRESHOLD] bounces within [SOFT_BOUNCE_WINDOW_DAYS] days.
     */
    @Transactional
    fun recordSoftBounce(email: String) {
        val normalized = email.lowercase()
        val existing = repository.findByEmail(normalized)
        val now = Instant.now()

        if (existing != null) {
            // Don't downgrade a hard bounce / complaint to soft bounce tracking
            if (existing.reason == SuppressionReason.HARD_BOUNCE || existing.reason == SuppressionReason.COMPLAINT) {
                return
            }

            val windowStart = now.minus(SOFT_BOUNCE_WINDOW_DAYS.toLong(), ChronoUnit.DAYS)
            if (existing.firstSeenAt.isBefore(windowStart)) {
                existing.firstSeenAt = now
                existing.bounceCount = 1
            } else {
                existing.bounceCount += 1
            }
            existing.lastSeenAt = now

            if (existing.bounceCount >= SOFT_BOUNCE_THRESHOLD) {
                existing.reason = SuppressionReason.SOFT_BOUNCE_THRESHOLD
                log.info("Suppressed email (soft bounce threshold): {} (count={})", normalized, existing.bounceCount)
            }
            repository.save(existing)
        } else {
            repository.save(
                EmailSuppression(
                    email = normalized,
                    reason = SuppressionReason.SOFT_BOUNCE_THRESHOLD,
                    bounceCount = 1,
                )
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(EmailSuppressionService::class.java)
        private const val SOFT_BOUNCE_THRESHOLD = 5
        private const val SOFT_BOUNCE_WINDOW_DAYS = 7
    }
}
