package net.blueshell.api.domain.user.application.contact

import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.Role

/**
 * Domain interface for contact synchronization with external systems (ADR-019: Anti-Corruption Layer)
 *
 * This interface defines domain-friendly contact operations without exposing
 * external contact management system details (e.g., Brevo, Mailchimp, SendGrid).
 *
 * Platform layer provides concrete implementations (BrevoContactAdapter).
 */
interface ContactSyncAdapter {
    /**
     * Synchronize a user's contact information with the external system.
     * If the contact doesn't exist, it will be created.
     * If the contact exists, it will be updated.
     *
     * @param userId The domain user ID (for tracking)
     * @param contactData The contact data to sync
     * @return External contact ID
     * @throws ContactServiceException if the operation fails
     */
    fun syncContact(userId: Long, contactData: ContactData): String

    /**
     * Get and update the external contact ID for a user.
     * Used to fetch the contact ID from external system if not yet stored.
     *
     * @param userId The domain user ID
     * @param email The user's email address (used for lookup)
     * @return External contact ID (null if not found)
     * @throws ContactServiceException if the operation fails
     */
    fun getContactId(userId: Long, email: String): String?

    /**
     * Add a contact to an external list.
     *
     * @param listId The external list ID
     * @param contactId The external contact ID
     * @throws ContactServiceException if the operation fails
     */
    fun addToList(listId: String, contactId: String)

    /**
     * Remove a contact from an external list.
     *
     * @param listId The external list ID
     * @param contactId The external contact ID
     * @throws ContactServiceException if the operation fails
     */
    fun removeFromList(listId: String, contactId: String)

    /**
     * Delete a contact from the external system.
     *
     * @param contactId The external contact ID
     * @throws ContactServiceException if the operation fails
     */
    fun deleteContact(contactId: String)

    /**
     * Create a new contact list in the external system.
     *
     * @param listName The name for the list
     * @param folderName The folder to create the list in
     * @return External list ID
     * @throws ContactServiceException if the operation fails
     */
    fun createList(listName: String, folderName: String): String
}

/**
 * Domain data for contact synchronization.
 * Contains only the information needed for external contact systems,
 * isolated from domain entity structure.
 */
data class ContactData(
    val email: String,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String?,
    val newsletter: Boolean,
    val isMember: Boolean,
    val attributes: Map<String, Any> = emptyMap()
)

fun User.toContactData(): ContactData = ContactData(
    email = this.email,
    firstName = this.firstName,
    lastName = this.lastName,
    phoneNumber = this.phoneNumber,
    newsletter = this.newsletter,
    isMember = this.hasRole(Role.MEMBER)
)

/**
 * Domain exception for contact operations.
 * Thrown when contact adapter operations fail.
 */
class ContactServiceException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
