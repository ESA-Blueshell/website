package net.blueshell.api.contact.api

import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.Role

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
open class ContactServiceException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
