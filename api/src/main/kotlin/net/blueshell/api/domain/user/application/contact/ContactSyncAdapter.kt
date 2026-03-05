package net.blueshell.api.domain.user.application.contact

import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.Role

/**
 * Domain interface for contact synchronization with external systems (ADR-019: Anti-Corruption Layer)
 *
 * Adapters are responsible for a single external system. Multiple adapters may be active at the
 * same time; the ContactSyncService orchestrates fanout across all registered implementations.
 *
 * All IDs are system-specific Longs. The orchestration service resolves domain IDs to system IDs
 * before calling adapter methods.
 */
interface ContactSyncAdapter {
    val system: ContactSystem

    /**
     * Creates a new contact in the external system.
     *
     * @param data The contact data to create
     * @return System-specific contact ID
     * @throws ContactServiceException if the operation fails
     */
    fun createContact(data: ContactData): Long

    /**
     * Updates an existing contact in the external system.
     *
     * @param externalId The external contact ID (from a previously created contact)
     * @param data Updated contact data
     * @throws ContactServiceException if the operation fails
     */
    fun updateContact(externalId: Long, data: ContactData)

    /**
     * Deletes the contact identified by its external ID.
     *
     * @param externalId The external contact ID
     * @throws ContactServiceException if the operation fails
     */
    fun deleteContact(externalId: Long)
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
