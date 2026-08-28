package net.blueshell.api.contact.domain

import net.blueshell.api.contact.api.ContactServiceException
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.json.JsonMapper

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

/** Parsed shape of a Brevo error response body. */
internal data class BrevoError(
    val code: String?,
    val message: String?,
    val duplicateIdentifiers: List<String>,
)

internal const val DUPLICATE_PARAMETER: String = "duplicate_parameter"
internal const val INVALID_PARAMETER: String = "invalid_parameter"
internal const val DOCUMENT_NOT_FOUND: String = "document_not_found"

private val parseLog = LoggerFactory.getLogger("net.blueshell.api.contact.domain.BrevoError")

internal fun parseBrevoError(e: RestClientResponseException, jsonMapper: JsonMapper): BrevoError? {
    val body = e.responseBodyAsString.takeIf { it.isNotBlank() } ?: return null
    return try {
        val map = jsonMapper.readValue(body, Map::class.java)
        val metadata = map["metadata"] as? Map<*, *>
        val ids = (metadata?.get("duplicate_identifiers") as? List<*>)
            ?.mapNotNull { it as? String }
            ?: emptyList()
        BrevoError(
            code = map["code"] as? String,
            message = map["message"] as? String,
            duplicateIdentifiers = ids,
        )
    } catch (ex: Exception) {
        parseLog.warn("Could not parse Brevo error body: {}", body, ex)
        null
    }
}
