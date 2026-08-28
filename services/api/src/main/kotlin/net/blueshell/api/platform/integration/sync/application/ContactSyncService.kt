package net.blueshell.api.platform.integration.sync.application

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.contact.api.ContactData
import net.blueshell.api.contact.api.toContactData
import net.blueshell.api.contact.persistence.Contact
import net.blueshell.api.contact.persistence.ContactRepository
import net.blueshell.api.platform.integration.sync.port.SyncTargetRegistry
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.enums.ContactSystem
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import net.blueshell.api.contact.api.toContactData

/**
 * Drives contact sync to every registered contact target.
 *
 * Reads the user inside the transaction and delegates the per-target push +
 * mapping bookkeeping to [SyncFanOut]. Each push also writes back to
 * `Contact.externalId` so the legacy list-membership handler keeps working
 * until that column is dropped.
 */
@Service
class ContactSyncService(
    private val registry: SyncTargetRegistry,
    private val fanOut: SyncFanOut,
    private val userService: UserService,
    private val contactRepository: ContactRepository,
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
    fun remove(userId: Long) {
        contactRepository.findByUserId(userId)?.let { contactRepository.softDeleteById(it.id!!) }
        push(userId, null)
    }

    private fun push(userId: Long, data: ContactData?) {
        fanOut.push(AGGREGATE, userId, data, registry.forContact()) { system, externalId ->
            bridgeToLegacyContact(userId, system, externalId)
        }
    }

    private fun bridgeToLegacyContact(userId: Long, system: TargetSystem, externalId: String?) {
        val contactSystem = when (system) {
            TargetSystem.BREVO -> ContactSystem.BREVO
            else -> return
        }
        val contact = contactRepository.findByUserId(userId) ?: contactRepository.save(Contact(userId = userId))
        if (externalId == null) contact.clearExternalId(contactSystem)
        else contact.setExternalId(contactSystem, externalId.toLong())
        contactRepository.save(contact)
    }

    companion object {
        private const val AGGREGATE = "USER"
        private val log = LoggerFactory.getLogger(ContactSyncService::class.java)
    }
}
