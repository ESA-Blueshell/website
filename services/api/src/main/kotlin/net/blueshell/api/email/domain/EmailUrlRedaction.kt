package net.blueshell.api.email.domain

/**
 * Strips the URLs out of an email rendered for inspection.
 *
 * A sent body routinely carries a credential in a link — an activation token, a guest token, an
 * unsubscribe key — and following one spends a one-time token on the wrong person. Redacted
 * here, on the way out of the api, so the browser never receives the URL at all. A link's
 * visible text survives, so the email still reads as written, and so do `data:` URIs,
 * which are the classpath images the preview renderer inlined and reach nothing.
 */
object EmailUrlRedaction {

    /** Stands in for a removed link, in the markup and in the text. */
    const val PLACEHOLDER: String = "[link removed]"

    /** Attributes that make the browser fetch or navigate somewhere. */
    private val URL_ATTRIBUTES = listOf("href", "src", "action", "background", "poster", "srcset")

    /**
     * Matches an attribute's whole `name="value"` form, quoted either way.
     *
     * `[^"']*` cannot span the closing quote, so there is nothing to backtrack over — which
     * matters because these documents carry inlined base64 images hundreds of kilobytes long.
     */
    private val ATTRIBUTE_PATTERN = Regex(
        """(${URL_ATTRIBUTES.joinToString("|")})\s*=\s*("([^"]*)"|'([^']*)')""",
        RegexOption.IGNORE_CASE,
    )

    /** A bare URL sitting in text, where the link text was the link. */
    private val BARE_URL_PATTERN = Regex("""\b(?:https?|mailto|ftp)://?[^\s"'<>()\[\]]+""", RegexOption.IGNORE_CASE)

    /** A `url(...)` reference inside a style attribute or block. */
    private val CSS_URL_PATTERN = Regex("""url\(\s*['"]?(?!data:)[^)'"]*['"]?\s*\)""", RegexOption.IGNORE_CASE)

    /**
     * Returns [html] with every URL removed.
     *
     * `data:` values are left alone: they are the inlined preview images, and they address
     * nothing. Everything else loses its target — attributes are emptied rather than dropped,
     * so the markup stays valid and a mail client's layout still holds.
     */
    fun redact(html: String): String {
        val withoutAttributes = ATTRIBUTE_PATTERN.replace(html) { match ->
            val name = match.groupValues[1]
            val value = match.groupValues[3].ifEmpty { match.groupValues[4] }
            if (isSafe(value)) match.value else "$name=\"\""
        }
        val withoutCssUrls = CSS_URL_PATTERN.replace(withoutAttributes, "none")
        return BARE_URL_PATTERN.replace(withoutCssUrls, PLACEHOLDER)
    }

    /**
     * True for values that address nothing outside the document: the inlined images, an
     * in-page anchor, and an already-emptied attribute.
     */
    private fun isSafe(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.isEmpty() || trimmed.startsWith("data:", ignoreCase = true) || trimmed.startsWith("#")
    }
}
