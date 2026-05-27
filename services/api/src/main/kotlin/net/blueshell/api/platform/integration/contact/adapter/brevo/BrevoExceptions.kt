package net.blueshell.api.platform.integration.contact.adapter.brevo

import net.blueshell.api.platform.integration.contact.adapter.ContactServiceException

/**
 * Which Brevo unique identifier collided on a create. Brevo reports these in
 * the error body under `metadata.duplicate_identifiers` (e.g. `["email"]`).
 */
enum class BrevoDuplicateIdentifier {
    EMAIL,
    SMS,
    OTHER;

    companion object {
        fun from(raw: String): BrevoDuplicateIdentifier = when (raw.trim().lowercase()) {
            "email" -> EMAIL
            "sms" -> SMS
            else -> OTHER
        }
    }
}

/**
 * A Brevo Contacts API call failed with a structured error we could not recover
 * from. Carries the parsed HTTP status, Brevo error code and human-readable
 * message so the cause is visible without digging through the response body.
 */
class BrevoApiException(
    val statusCode: Int,
    val brevoCode: String?,
    val brevoMessage: String?,
    operation: String,
    cause: Throwable? = null,
) : ContactServiceException(buildMessage(statusCode, brevoCode, brevoMessage, operation), cause) {
    companion object {
        private fun buildMessage(status: Int, code: String?, message: String?, operation: String): String {
            val codePart = code?.let { " $it" } ?: ""
            val messagePart = message?.let { " — $it" } ?: ""
            return "Brevo $operation failed: $status$codePart$messagePart"
        }
    }
}

/**
 * Brevo rejected a create because the contact already exists, but the existing
 * contact could not be looked up by any of the duplicated identifiers (so it
 * could not be adopted). Surfaces exactly which identifiers collided.
 */
class BrevoDuplicateContactException(
    val duplicates: Set<BrevoDuplicateIdentifier>,
    val email: String?,
    val phone: String?,
    cause: Throwable? = null,
) : ContactServiceException(
    "Brevo contact already exists (duplicate ${duplicates.joinToString(", ")}) " +
        "but could not be resolved for email=$email phone=$phone",
    cause,
)
