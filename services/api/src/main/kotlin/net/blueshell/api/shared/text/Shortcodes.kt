package net.blueshell.api.shared.text

/**
 * Emoji shortcodes in a description, found where the site's renderer expanded them: outside code,
 * addresses and anything written in angle brackets, which holds mentions, server emoji and
 * timestamps. A shortcode is expanded only where [names] knows it, so `12:30:45` stays as it is.
 */
object Shortcodes {
    // Any case: the site's names are lower case, a server emoji's name need not be.
    private val SHORTCODE = Regex("""^:([A-Za-z0-9_+-]+):""")
    private val WORDY = Regex("""[A-Za-z]""")
    private val ADDRESS = Regex("""^(https?://|www\.)\S+""")
    private val BRACKETED = Regex("""^<[^\s<>][^<>\n]*>""")
    private val FENCE = Regex("""^ {0,3}(`{3,}|~{3,})""")
    private const val TABLE = "/db/changelog/emoji/emojilib-shortcodes.tsv"

    /** The names the site expanded, from the table committed beside the changelog. */
    fun names(): Map<String, String> =
        (Shortcodes::class.java.getResourceAsStream(TABLE) ?: error("The shortcode table $TABLE is missing"))
            .bufferedReader()
            .useLines { lines ->
                lines
                    .filterNot { it.isBlank() || it.startsWith("#") }
                    .associate { line -> line.substringBefore('\t') to line.substringAfter('\t') }
            }

    /** [text] with every shortcode [names] knows written as its emoji, and how many there were. */
    fun rewrite(
        text: String,
        names: Map<String, String>,
    ): Pair<String, Int> = resolve(text, names::get).let { it.text to it.resolved.size }

    /**
     * [text] with every shortcode [emojiOf] answers written as that emoji, the names it answered,
     * and the names it did not that read as names rather than numbers, as `12:30:45` holds `30`.
     */
    fun resolve(
        text: String,
        emojiOf: (String) -> String?,
    ): ShortcodesResolved {
        val out = StringBuilder(text.length)
        val found = Found(emojiOf)
        var fence: String? = null
        for (line in text.split('\n')) {
            val opened = FENCE.find(line)?.groupValues?.get(1)
            when {
                fence != null -> {
                    if (opened != null && opened[0] == fence[0] && opened.length >= fence.length) fence = null
                    out.append(line)
                }
                opened != null -> {
                    fence = opened
                    out.append(line)
                }
                else -> rewriteLine(line, found, out)
            }
            out.append('\n')
        }
        out.setLength(out.length - 1)
        return ShortcodesResolved(out.toString(), found.resolved, found.left)
    }

    private class Found(
        val emojiOf: (String) -> String?,
    ) {
        val resolved = mutableListOf<String>()
        val left = mutableListOf<String>()
    }

    private fun rewriteLine(
        line: String,
        found: Found,
        out: StringBuilder,
    ) {
        var at = 0
        while (at < line.length) {
            val rest = line.substring(at)
            val literal = literalLength(rest)
            if (literal > 0) {
                out.append(rest, 0, literal)
                at += literal
                continue
            }
            val shortcode = SHORTCODE.find(rest)
            val name = shortcode?.groupValues?.get(1)
            val emoji = name?.let(found.emojiOf)
            if (emoji != null) {
                out.append(emoji)
                at += shortcode.value.length
                found.resolved += name
            } else {
                if (name != null && WORDY.containsMatchIn(name)) found.left += name
                out.append(line[at])
                at++
            }
        }
    }

    // How much of [rest] the renderer took as it stands: an escaped character, a code span, an
    // address, a link's target or anything in angle brackets.
    private fun literalLength(rest: String): Int =
        when {
            rest.startsWith("\\") && rest.length > 1 -> 2
            rest.startsWith("`") -> codeSpanLength(rest)
            rest.startsWith("](") -> rest.indexOf(')').takeIf { it > 0 }?.plus(1) ?: 0
            else -> ADDRESS.find(rest)?.value?.length ?: BRACKETED.find(rest)?.value?.length ?: 0
        }

    // A run of backticks closes only on a run of the same length; an unclosed run is only backticks.
    private fun codeSpanLength(rest: String): Int {
        val ticks = rest.takeWhile { it == '`' }.length
        var from = ticks
        while (true) {
            val close = rest.indexOf("`".repeat(ticks), from)
            if (close < 0) return ticks
            val run = rest.substring(close).takeWhile { it == '`' }.length
            if (run == ticks) return close + ticks
            from = close + run
        }
    }
}

/** A text with its shortcodes written as emoji, the names that were, and the names no emoji answered. */
data class ShortcodesResolved(
    val text: String,
    val resolved: List<String>,
    val left: List<String>,
)
