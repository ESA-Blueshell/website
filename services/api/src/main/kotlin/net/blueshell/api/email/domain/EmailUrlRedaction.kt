package net.blueshell.api.email.domain

/**
 * Strips the URLs out of an email rendered for inspection.
 *
 * A sent email's body routinely carries a credential in a link: an activation or recovery
 * token, a guest access token, an unsubscribe key. Anyone reading the outbox is reading
 * somebody else's mail, and a link there is worse than useless — following it spends a
 * one-time token on the wrong person, and a remote image announces the read to whoever hosts
 * it. So the redaction happens here, on the way out of the api: the browser never receives
 * the URL, which is the only place the property can actually hold.
 *
 * What survives: the visible text of a link, so the email still reads as it was written, and
 * `data:` URIs, which are the images the preview renderer inlined from the classpath and
 * reach nothing.
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
