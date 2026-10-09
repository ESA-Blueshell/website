package net.blueshell.api.shared.discord

/**
 * A description with two of Discord's line rules written in CommonMark's terms: `>>>` quotes
 * everything after it, and a list ends at the first line that is neither an item nor indented
 * under one. [original] says where a place in [text] was in the description.
 */
internal class DiscordLines private constructor(
    val text: String,
    private val lines: List<Line>,
) {
    // A line of [text] at [start], read from the description at [from], with a different mark before it.
    private data class Line(
        val start: Int,
        val from: Int,
        val mark: Int,
        val markFrom: Int,
    )

    fun original(at: Int): Int {
        val line = lines[lines.binarySearchBy(at) { it.start }.let { if (it >= 0) it else -it - 2 }.coerceAtLeast(0)]
        val within = at - line.start
        return if (within < line.mark) line.from + minOf(within, line.markFrom) else line.from + line.markFrom + within - line.mark
    }

    companion object {
        private val FENCE = Regex("^\\s{0,3}(?:```|~~~)")
        private val ITEM = Regex("^\\s*(?:[-*+]|\\d+[.)])\\s")
        private val QUOTE_REST = Regex("^>>> ?")
        private val INDENTED = Regex("^\\s+\\S")
        private const val QUOTE = "> "

        // A line that is neither an item nor indented under one, which ends a list in Discord.
        private fun unmarked(line: String) = line.isNotBlank() && !line.first().isWhitespace() && !ITEM.containsMatchIn(line)

        fun of(description: String): DiscordLines {
            val written = description.split("\n")
            val text = StringBuilder()
            val lines = mutableListOf<Line>()
            var from = 0

            fun add(
                line: String,
                mark: String,
                markFrom: Int,
            ) {
                lines += Line(text.length, from, mark.length, markFrom)
                text.append(mark).append(line.substring(markFrom)).append("\n")
            }

            var fenced = false
            var listed = false
            for ((at, line) in written.withIndex()) {
                if (FENCE.containsMatchIn(line)) fenced = !fenced
                val quoted = if (fenced) null else QUOTE_REST.find(line)
                if (quoted != null) {
                    add(line, QUOTE, quoted.value.length)
                    from += line.length + 1
                    for (rest in written.drop(at + 1)) {
                        add(rest, QUOTE, 0)
                        from += rest.length + 1
                    }
                    break
                }
                if (!fenced && listed && unmarked(line)) add("", "", 0)
                listed = !fenced && (ITEM.containsMatchIn(line) || (listed && INDENTED.containsMatchIn(line)))
                add(line, "", 0)
                from += line.length + 1
            }
            return DiscordLines(text.toString().removeSuffix("\n"), lines)
        }
    }
}
