package net.blueshell.api.platform.integration.sync.application

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.contact.adapter.ContactData
import net.blueshell.api.platform.integration.contact.adapter.toContactData
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.platform.integration.sync.port.ContactSyncTarget
import net.blueshell.api.platform.integration.sync.port.SyncTargetRegistry
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.enums.ContactSystem
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Drives contact sync to every registered [ContactSyncTarget].
 *
 * Reads the user inside the transaction, fans out, persists each target's
 * external id in `external_id_mapping`. Also bridges to `Contact.externalId`
 * so the list-membership handler keeps working until that column is dropped.
 */
@Service
class ContactSyncService(
    private val registry: SyncTargetRegistry,
    private val mappings: ExternalIdMappingService,
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
        val contact = contactRepository.findByUserId(userId)
        if (contact != null) {
            contactRepository.softDeleteById(contact.id!!)
        }
        push(userId, null)
    }

    private fun push(userId: Long, data: ContactData?) {
        registry.forContact().forEach { target ->
            val current = mappings.find(AGGREGATE, userId, target.system.name)?.externalId
            val newId = target.push(userId, data, current)
            mappings.upsert(AGGREGATE, userId, target.system.name, newId)
            bridgeToLegacyContact(userId, target.system, newId)
        }
    }

    private fun bridgeToLegacyContact(userId: Long, system: TargetSystem, externalId: String?) {
        val contactSystem = when (system) {
            TargetSystem.LISTMONK -> ContactSystem.LISTMONK
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
