package net.blueshell.api.platform.migration

/**
 * Emoji shortcodes in a description, found where the site's renderer expanded them: outside code,
 * addresses and anything written in angle brackets, which holds mentions, server emoji and
 * timestamps. A shortcode is expanded only where [names] knows it, so `12:30:45` stays as it is.
 */
internal object Shortcodes {
    private val SHORTCODE = Regex("""^:([a-z0-9_+-]+):""")
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
    ): Pair<String, Int> {
        val out = StringBuilder(text.length)
        var count = 0
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
                else -> count += rewriteLine(line, names, out)
            }
            out.append('\n')
        }
        out.setLength(out.length - 1)
        return out.toString() to count
    }

    private fun rewriteLine(
        line: String,
        names: Map<String, String>,
        out: StringBuilder,
    ): Int {
        var count = 0
        var at = 0
        while (at < line.length) {
            val rest = line.substring(at)
            val literal = literalLength(rest)
            if (literal > 0) {
                out.append(rest, 0, literal)
                at += literal
                continue
            }
            val emoji = SHORTCODE.find(rest)?.let { found -> names[found.groupValues[1]]?.let { found.value.length to it } }
            if (emoji != null) {
                out.append(emoji.second)
                at += emoji.first
                count++
            } else {
                out.append(line[at])
                at++
            }
        }
        return count
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
