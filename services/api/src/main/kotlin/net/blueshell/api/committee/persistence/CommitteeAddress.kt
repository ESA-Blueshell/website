package net.blueshell.api.committee.persistence

/** The longest address a committee's page answers to, which is the column's width. */
const val ADDRESS_LENGTH = 64

/**
 * The address [text] makes: lowercase letters and digits, everything else one hyphen. Mirrored by
 * the committee-page changeset, which made every existing committee's address this way.
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
