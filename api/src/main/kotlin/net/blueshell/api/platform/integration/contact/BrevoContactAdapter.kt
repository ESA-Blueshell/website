package net.blueshell.api.platform.integration.contact

import net.blueshell.api.domain.user.application.contact.ContactData
import net.blueshell.api.domain.user.application.contact.ContactServiceException
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException

/**
 * Brevo Contact Anti-Corruption Layer (ADR-019)
 *
 * This adapter translates between domain concepts and Brevo Contact API.
 * It protects the domain from:
 * - Brevo API structure and breaking changes
 * - Brevo-specific error handling
 * - Brevo authentication details
 * - Brevo data model
 *
 * Benefits:
 * - Domain is unaware of Brevo specifics
 * - Easy to replace with Mailchimp, SendGrid, etc.
 * - All Brevo API errors are translated to domain exceptions
 * - Testing is simplified (mock the adapter interface)
 *
 * Active in production profile only (test/dev use MockContactAdapter)
 */
@Service
@Primary
@Profile("!test & !dev")
class BrevoContactAdapter(
    private val brevoClient: BrevoContactClient
) : ContactSyncAdapter {

    override fun syncContact(userId: Long, contactId: String?, contactData: ContactData): String {
        log.info("Syncing contact for user {}: {}", userId, contactData.email)
        // Check if contact exists in Brevo
        val contactId = contactId ?: brevoClient.getContactIdByEmail(contactData.email)

        val attributes = buildAttributes(contactData)
        val externalId = userId.toString()

        return try {
            if (contactId != null) {
                // Contact exists - update it
                brevoClient.updateContact(
                    contactId = contactId.toString(),
                    email = contactData.email,
                    externalId = externalId,
                    attributes = attributes
                )
                contactId.toString()
            } else {
                // Contact doesn't exist - create it
                val createdId = brevoClient.createContact(
                    email = contactData.email,
                    externalId = externalId,
                    attributes = attributes
                )
                createdId.toString()
            }
        } catch (e: RestClientResponseException) {
            log.error("Failed to sync contact for user {} to Brevo", userId, e)


            throw ContactServiceException(
                "Failed to sync contact(id=${contactId}) \nemail=${contactData.email} \nexternalId=${userId} \nattributes=${attributes}\n",
                e
            )
        }
    }

    override fun getContactId(userId: Long, email: String): String? {
        log.debug("Getting contact ID for user {}: {}", userId, email)

        return try {
            val contactId = brevoClient.getContactIdByEmail(email)
            contactId?.toString()
        } catch (e: RestClientResponseException) {
            log.error("Failed to get contact ID for user {}", userId, e)
            throw ContactServiceException("Failed to get contact ID", e)
        }
    }

    override fun addToList(listId: String, contactId: String) {
        log.info("Adding contact {} to Brevo list {}", contactId, listId)

        try {
            brevoClient.addContactsToList(
                listId = listId.toLong(),
                contactIds = listOf(contactId.toLong())
            )
        } catch (e: RestClientResponseException) {
            log.error("Failed to add contact {} to list {}", contactId, listId, e)
            throw ContactServiceException("Failed to add contact to list", e)
        } catch (e: NumberFormatException) {
            log.error("Invalid list ID or contact ID format: listId={}, contactId={}", listId, contactId, e)
            throw ContactServiceException("Invalid ID format", e)
        }
    }

    override fun removeFromList(listId: String, contactId: String) {
        log.info("Removing contact {} from Brevo list {}", contactId, listId)

        try {
            brevoClient.removeContactsFromList(
                listId = listId.toLong(),
                contactIds = listOf(contactId.toLong())
            )
        } catch (e: RestClientResponseException) {
            log.error("Failed to remove contact {} from list {}", contactId, listId, e)
            throw ContactServiceException("Failed to remove contact from list", e)
        } catch (e: NumberFormatException) {
            log.error("Invalid list ID or contact ID format: listId={}, contactId={}", listId, contactId, e)
            throw ContactServiceException("Invalid ID format", e)
        }
    }

    override fun deleteContact(contactId: String) {
        log.info("Deleting Brevo contact {}", contactId)

        try {
            brevoClient.deleteContact(contactId.toLong())
        } catch (e: RestClientResponseException) {
            log.error("Failed to delete contact {}", contactId, e)
            throw ContactServiceException("Failed to delete contact", e)
        } catch (e: NumberFormatException) {
            log.error("Invalid contact ID format: contactId={}", contactId, e)
            throw ContactServiceException("Invalid ID format", e)
        }
    }

    override fun createList(listName: String, folderName: String): String {
        log.info("Creating Brevo list: {} in folder: {}", listName, folderName)

        return try {
            // For now, we only support "contributionPeriods" folder
            val folderId = when (folderName) {
                "contributionPeriods" -> brevoClient.getContributionPeriodsFolderId()
                else -> throw ContactServiceException("Unknown folder: $folderName")
            }

            val listId = brevoClient.createList(listName, folderId)
            listId.toString()
        } catch (e: RestClientResponseException) {
            log.error("Failed to create Brevo list: {}", listName, e)
            throw ContactServiceException("Failed to create list", e)
        }
    }

    /**
     * Build Brevo-specific attributes from domain ContactData.
     * This is where Brevo's attribute naming conventions are isolated.
     */
    private fun buildAttributes(contactData: ContactData): Map<String, Any> {
        val attrs = mutableMapOf<String, Any>(
            "NEWSLETTER" to contactData.newsletter,
            "IS_MEMBER" to contactData.isMember,
            "FIRSTNAME" to contactData.firstName,
            "LASTNAME" to contactData.lastName,
            "SURNAME" to contactData.lastName
        )

        // Add phone number if available
        contactData.phoneNumber?.let { phone ->
            attrs["SMS"] = phone
            attrs["WHATSAPP"] = phone
        }

        // Add any additional attributes
        attrs.putAll(contactData.attributes)

        return attrs
    }

    companion object {
        private val log = LoggerFactory.getLogger(BrevoContactAdapter::class.java)
    }
}
