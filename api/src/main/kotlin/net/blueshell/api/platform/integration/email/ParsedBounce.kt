package net.blueshell.api.platform.integration.email

data class ParsedBounce(
    val originalMessageId: String,
    val status: String?,
    val action: BounceAction,
    val diagnosticCode: String?,
    val rawSubject: String,
)

enum class BounceAction { FAILED, DELAYED, OTHER }
