package net.blueshell.api.platform.integration.contact.application.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.platform.integration.sync.application.ContactSyncService
import net.blueshell.api.shared.job.ContactJobs
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

/** Daily bulk refresh of every user's contact state across all targets. */
@Component
class DispatchContactSyncsJob(
    objectMapper: ObjectMapper,
    private val userService: UserService,
    private val contactSync: ContactSyncService,
    transactionManager: PlatformTransactionManager,
) : AbstractJsonJobHandler<ContactJobs.DispatchContactSyncsPayload>(
    objectMapper,
    ContactJobs.DispatchContactSyncs.payloadType,
) {
    override val jobType: String = ContactJobs.DispatchContactSyncs.type

    // ContactSyncService.sync is @Transactional with default REQUIRED propagation,
    // so it would join the outer @Transactional opened by AbstractJsonJobHandler.handle.
    // A single user failing would then mark the shared transaction rollback-only,
    // and the dispatcher's commit at the end would throw UnexpectedRollbackException
    // even though runCatching swallowed the per-user exception. Each user gets its
    // own REQUIRES_NEW transaction so failures isolate to that user's row.
    private val perUserSync = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    override fun handlePayload(payload: ContactJobs.DispatchContactSyncsPayload) {
        val users = userService.findAll()
        log.info("Refreshing contact sync for {} users", users.size)
        users.forEach { user ->
            runCatching {
                perUserSync.executeWithoutResult { contactSync.sync(user.id!!) }
            }.onFailure { e ->
                log.error("Bulk contact sync failed for user {}: {}", user.id, e.message)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DispatchContactSyncsJob::class.java)
    }
}
