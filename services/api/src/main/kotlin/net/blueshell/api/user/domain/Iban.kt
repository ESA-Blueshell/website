package net.blueshell.api.user.domain

import net.blueshell.api.user.api.MaskedIban
import java.math.BigInteger

/**
 * An IBAN as the bank reads it: spaces gone, upper case, and its ISO 7064 check digits right.
 * The length per country is not checked here; the bank refuses a wrong one, and the check
 * digits already catch a mistyped character.
 */
@JvmInline
value class Iban private constructor(
    val value: String,
) {
    /** Its country code and its last two characters, as kept beside the sealed IBAN: all a view shows without a reveal. */
    val masked: String get() = value.take(MASK_END) + value.takeLast(MASK_END)

    override fun toString(): String = "Iban(${MaskedIban.of(masked)})"

    companion object {
        private const val LAST_SHOWN = 4
        private const val MASK_END = 2
        private val SHAPE = Regex("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$")
        private val NINETY_SEVEN = BigInteger.valueOf(97)

        /** The IBAN in [raw], or null where it is not one. */
        fun parse(raw: String): Iban? {
            val compact = raw.replace(" ", "").uppercase()
            if (!SHAPE.matches(compact)) return null
            val rearranged = compact.drop(LAST_SHOWN) + compact.take(LAST_SHOWN)
            val digits = rearranged.map { if (it.isLetter()) (it - 'A' + 10).toString() else it.toString() }.joinToString("")
            return if (BigInteger(digits).mod(NINETY_SEVEN) == BigInteger.ONE) Iban(compact) else null
        }
    }
}
