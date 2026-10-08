package net.blueshell.api.user.domain.sealing

import net.blueshell.api.shared.refusal.Refusal
import org.springframework.http.HttpStatus

/** One value to seal or open, with the context it is bound to: the field and whose it is. */
data class Sealed(
    val value: String,
    val context: String,
)

/** The vault that seals private details cannot be reached, so nothing is saved or shown. */
class SealingUnavailable(
    cause: Throwable? = null,
) : Refusal(HttpStatus.SERVICE_UNAVAILABLE, "SealingUnavailable", "Private details cannot be sealed or opened now.") {
    init {
        cause?.let(::initCause)
    }
}

/** A sealed value that does not open under the context given: copied from another row, or tampered with. */
class SealedValueUnopenable(
    cause: Throwable,
) : RuntimeException("A sealed value does not open under its context", cause)

/**
 * Seals and opens private details under a named key, each value bound to its own context. A value
 * opened under another context than it was sealed with fails, so a sealed value copied onto
 * another member's row does not open there. Nothing ever falls back to plaintext: when the key
 * cannot be reached, every call answers [SealingUnavailable].
 */
interface Sealer {
    /** Seals each [Sealed.value] under [key] and its context, in one call. */
    fun seal(
        key: String,
        values: List<Sealed>,
    ): List<String>

    /** Opens each sealed [Sealed.value]; one sealed under another context, or tampered with, fails with [SealedValueUnopenable]. */
    fun open(
        key: String,
        values: List<Sealed>,
    ): List<String>

    /**
     * Moves each sealed [Sealed.value] onto the newest version of [key] without opening it to the
     * api, in one call. A value that cannot be moved answers null and does not stop the others.
     */
    fun rewrap(
        key: String,
        values: List<Sealed>,
    ): List<String?>
}

/** The key version a sealed value sits on, which both sealers write as `scheme:vN:value`. */
fun keyVersionOf(sealed: String): Int =
    requireNotNull(
        sealed
            .split(':')
            .getOrNull(1)
            ?.removePrefix("v")
            ?.toIntOrNull(),
    ) { "A sealed value names its key version" }

/** The context a member's field is sealed under: the field and the user id. */
fun sealingContext(
    field: String,
    userId: Long,
): String = "$field:$userId"
