package net.blueshell.api.domain.user.application.lifecycle

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class UserLifecycleFinalizationScheduler(
    private val lifecycle: UserLifecycleService,
    @param:Value("\${app.user-lifecycle.finalization-batch-size:100}")
    private val batchSize: Int
) {
    @Scheduled(fixedDelayString = "\${app.user-lifecycle.finalization-fixed-delay-ms:900000}")
    fun finalizeExpiredUsers() {
        var totalFinalized = 0
        while (true) {
            val finalized = lifecycle.finalizeExpiredDeletedUsers(batchSize)
            totalFinalized += finalized
            if (finalized < batchSize) {
                break
            }
        }

        if (totalFinalized > 0) {
            log.info("Finalized {} expired deleted users", totalFinalized)
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(UserLifecycleFinalizationScheduler::class.java)
    }
}
