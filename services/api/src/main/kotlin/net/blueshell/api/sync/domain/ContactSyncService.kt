package net.blueshell.api.sync.domain

import net.blueshell.api.user.api.UserService
import net.blueshell.api.contact.api.ContactData
import net.blueshell.api.contact.api.toContactData
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Drives contact sync to every registered contact target.
 *
 * Reads the user inside the transaction and delegates the per-target push +
 * mapping bookkeeping to [SyncFanOut], which records each external id in
 * `external_id_mapping`.
 */
@Service
class ContactSyncService(
    private val registry: SyncTargetRegistry,
    private val fanOut: SyncFanOut,
    private val userService: UserService,
) {
    @Transactional
    fun sync(userId: Long) {
        val user = runCatching { userService.findById(userId) }.getOrNull() ?: run {
            log.warn("Contact sync skipped: user {} not found", userId)
            return
        }
        push(userId, user.toContactData())
    }

    @Transactional
    fun remove(userId: Long) = push(userId, null)

    private fun push(userId: Long, data: ContactData?) {
        fanOut.push(AGGREGATE, userId, data, registry.forContact())
    }

    companion object {
        private const val AGGREGATE = "USER"
        private val log = LoggerFactory.getLogger(ContactSyncService::class.java)
    }
}
