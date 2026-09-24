package net.blueshell.api.sync.domain

import net.blueshell.api.contact.api.ContactData
import net.blueshell.api.contact.api.toContactData
import net.blueshell.api.user.api.UserService
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
    /** Answers why nothing was pushed, or null where it was. */
    @Transactional
    fun sync(userId: Long): String? {
        val user = runCatching { userService.findById(userId) }.getOrNull() ?: return "The user no longer exists."
        push(userId, user.toContactData())
        return null
    }

    @Transactional
    fun remove(userId: Long) = push(userId, null)

    private fun push(
        userId: Long,
        data: ContactData?,
    ) {
        fanOut.push(AGGREGATE, userId, data, registry.forContact())
    }

    companion object {
        private const val AGGREGATE = "USER"
    }
}
