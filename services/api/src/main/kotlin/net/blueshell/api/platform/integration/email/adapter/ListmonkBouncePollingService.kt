package net.blueshell.api.platform.integration.email.adapter

import net.blueshell.api.platform.integration.email.application.service.EmailService
import net.blueshell.clients.listmonk.api.BouncesApi
import net.blueshell.clients.listmonk.model.BounceRecord
import net.blueshell.clients.listmonk.model.GetBouncesOrderByParameter
import net.blueshell.clients.listmonk.model.GetBouncesOrderParameter
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Periodically polls Listmonk's `GET /api/bounces` endpoint and updates outbox records.
 *
 * Since Listmonk returns no per-message IDs, bounces are matched to outbox records
 * by recipient email + sent-at time window.
 */
@Service
@Profile("!test")
class ListmonkBouncePollingService(
    private val bouncesApi: BouncesApi,
    private val emailService: EmailService,
) {
    @Volatile
    private var lastPollTime: Instant = Instant.now().minus(1, ChronoUnit.HOURS)

    @Scheduled(fixedDelayString = "\${listmonk.bounce.poll-interval-ms:300000}")
    fun pollBounces() {
        val pollStart = Instant.now()
        log.debug("Polling Listmonk bounces since {}", lastPollTime)

        try {
            val response = bouncesApi.getBounces(
                null,  // campaignId
                1,     // page
                1000,  // perPage — fetch up to 1000 most recent, ordered by created_at desc
                null,  // source
                GetBouncesOrderByParameter.CREATED_AT,
                GetBouncesOrderParameter.DESC,
            )

            val results: List<BounceRecord> = response?.data?.results ?: emptyList()

            // Only process bounces newer than our last poll time
            val newBounces = results.filter { bounce ->
                val createdAt = bounce.createdAt?.toInstant() ?: return@filter false
                createdAt.isAfter(lastPollTime)
            }

            if (newBounces.isNotEmpty()) {
                log.info("Processing {} new bounces from Listmonk", newBounces.size)
                newBounces.forEach { processBounce(it) }
            }

            lastPollTime = pollStart
        } catch (e: Exception) {
            log.error("Failed to poll Listmonk bounces: {}", e.message, e)
        }
    }

    private fun processBounce(bounce: BounceRecord) {
        val email = bounce.email ?: return
        val bounceType = bounce.type ?: "hard"

        // Match to an outbox record by email + time window (look back 24 hours)
        val since = Instant.now().minus(24, ChronoUnit.HOURS)
        val outboxEntry = emailService.findRecentByRecipientEmail(email, since)
        if (outboxEntry != null) {
            emailService.markBounced(outboxEntry, "Listmonk bounce type=$bounceType")
            log.info("Marked email id={} as BOUNCED for recipient={} type={}", outboxEntry.id, email, bounceType)
        } else {
            log.debug("No recent outbox entry found for bounce recipient={}", email)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ListmonkBouncePollingService::class.java)
    }
}
