package net.blueshell.api.sync.domain

import net.blueshell.api.contact.api.ContactAdapter
import net.blueshell.api.contact.api.ContactData
import net.blueshell.api.contact.api.toContactData
import net.blueshell.api.sync.api.ExternalIdMappingService
import net.blueshell.api.sync.api.ExternalIdMappingService.Companion.USER_AGGREGATE
import net.blueshell.api.user.api.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Pushes a user to the contact system and records the contact's id there in `external_id_mapping`. */
@Service
class ContactSyncService(
    private val contacts: ContactAdapter,
    private val mappings: ExternalIdMappingService,
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

    // Null data means the user should not be there.
    private fun push(
        userId: Long,
        data: ContactData?,
    ) {
        val system = contacts.system.name
        val current = mappings.find(USER_AGGREGATE, userId, system)?.externalId?.toLong()
        val next =
            when {
                data == null -> {
                    current?.let(contacts::deleteContact)
                    null
                }
                current == null -> contacts.createContact(data)
                else -> contacts.updateContact(current, data)
            }
        mappings.upsert(USER_AGGREGATE, userId, system, next?.toString())
    }
}
