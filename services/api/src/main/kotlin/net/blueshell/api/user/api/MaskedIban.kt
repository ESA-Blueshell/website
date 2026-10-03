package net.blueshell.api.user.api

/**
 * An IBAN as it shows without a reveal: its country code and its last two characters, written
 * out as `NL•• … ••34`. It is kept beside the sealed IBAN as four characters, `NL34`, so no row
 * and no response holds more of it.
 */
data class MaskedIban(
    val country: String,
    val lastTwo: String,
) {
    override fun toString(): String = "$country•• … ••$lastTwo"

    companion object {
        /** The masked IBAN kept as [stored], or null where none is kept. */
        fun of(stored: String?): MaskedIban? = stored?.takeIf { it.length == STORED }?.let { MaskedIban(it.take(2), it.takeLast(2)) }

        private const val STORED = 4
    }
}
