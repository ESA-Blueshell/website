package net.blueshell.api.shared.model

/** The longest address a page answers to, which is the width of every address column. */
const val ADDRESS_LENGTH = 64

/**
 * The address [text] makes for a page: lowercase letters and digits, of any alphabet, and
 * everything else one hyphen, none at either end. TWIN: `addressOf` in `src/utils/address.ts`,
 * which fills an address from a name as it is typed. Change one, change the other.
 */
fun addressOf(text: String): String =
    text
        .trim()
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else '-' }
        .joinToString("")
        .replace(Regex("-+"), "-")
        .trim('-')
        .take(ADDRESS_LENGTH)
