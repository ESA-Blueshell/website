package net.blueshell.api.user.domain

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import net.blueshell.api.user.api.UserErasureService

@Component
class ErasureScheduler(
    private val erasure: UserErasureService,
    @param:Value("\${app.user-erasure.finalization-batch-size:100}")
    private val batchSize: Int
) {
    @Scheduled(fixedDelayString = "\${app.user-erasure.finalization-fixed-delay-ms:900000}")
    fun finalizeExpiredUsers() {
        var totalFinalized = 0
        while (true) {
            val finalized = erasure.finalizeExpiredDeletedUsers(batchSize)
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
        private val log = LoggerFactory.getLogger(ErasureScheduler::class.java)
    }
}
