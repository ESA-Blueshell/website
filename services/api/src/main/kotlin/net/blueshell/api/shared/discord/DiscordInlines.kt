package net.blueshell.api.shared.discord

import net.blueshell.api.shared.discord.DescriptionNodeKind.CHANNEL_MENTION
import net.blueshell.api.shared.discord.DescriptionNodeKind.CODE
import net.blueshell.api.shared.discord.DescriptionNodeKind.EMOJI
import net.blueshell.api.shared.discord.DescriptionNodeKind.EMPHASIS
import net.blueshell.api.shared.discord.DescriptionNodeKind.IMAGE
import net.blueshell.api.shared.discord.DescriptionNodeKind.LINE_BREAK
import net.blueshell.api.shared.discord.DescriptionNodeKind.LINK
import net.blueshell.api.shared.discord.DescriptionNodeKind.ROLE_MENTION
import net.blueshell.api.shared.discord.DescriptionNodeKind.SERVER_EMOJI
import net.blueshell.api.shared.discord.DescriptionNodeKind.SPOILER
import net.blueshell.api.shared.discord.DescriptionNodeKind.STRIKE
import net.blueshell.api.shared.discord.DescriptionNodeKind.STRONG
import net.blueshell.api.shared.discord.DescriptionNodeKind.TEXT
import net.blueshell.api.shared.discord.DescriptionNodeKind.TIMESTAMP
import net.blueshell.api.shared.discord.DescriptionNodeKind.UNDERLINE
import net.blueshell.api.shared.discord.DescriptionNodeKind.USER_MENTION
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.parser.InlineParserContext
import org.commonmark.parser.SourceLines
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * The inline half of Discord's markdown: the text of one paragraph, heading, subtext or cell read
 * into nodes. Each rule is tried where the text stands, in Discord's order, and the first to match
 * takes what it matches, as Discord reads a message; CommonMark's rules for pairing marks do not
 * apply. Places are in the text the block came from.
 */
internal class DiscordInlines(
    private val context: InlineParserContext,
    lines: SourceLines,
) {
    private val content: String
    private val at: IntArray

    init {
        val joined = StringBuilder()
        val places = mutableListOf<Int>()
        for ((index, line) in lines.lines.withIndex()) {
            if (index > 0) {
                places += lines.lines[index - 1].sourceSpan?.let { it.inputIndex + it.length } ?: places.lastOrNull() ?: 0
                joined.append('\n')
            }
            val span = line.sourceSpan
            line.content.forEachIndexed { within, _ ->
                places += span?.let { it.inputIndex + minOf(within, it.length - 1).coerceAtLeast(0) } ?: within
            }
            joined.append(line.content)
        }
        places += lines.lines
            .lastOrNull()
            ?.sourceSpan
            ?.let { it.inputIndex + it.length } ?: joined.length
        content = joined.toString()
        at = places.toIntArray()
    }

    private val addresses: Map<Int, Address> = addressesIn(content)

    fun read(): List<DescriptionNode> = inlines(0, content.length, linked = false)

    private fun inlines(
        from: Int,
        to: Int,
        linked: Boolean,
    ): List<DescriptionNode> {
        val read = mutableListOf<DescriptionNode>()
        var plain = from
        var here = from
        while (here < to) {
            val found = ruleAt(here, from, to, linked)
            if (found == null) {
                here += Character.charCount(content.codePointAt(here))
                continue
            }
            read += plainOf(plain, here)
            read += found.nodes
            here = found.end
            plain = here
        }
        read += plainOf(plain, to)
        return joined(read)
    }

    private class Found(
        val nodes: List<DescriptionNode>,
        val end: Int,
    )

    private fun ruleAt(
        here: Int,
        from: Int,
        to: Int,
        linked: Boolean,
    ): Found? =
        when (content[here]) {
            '|' -> pair(SPOILER, SPOILER_MARKS, here, to, linked)
            '_' -> pair(UNDERLINE, UNDERLINE_MARKS, here, to, linked) ?: underscored(here, from, to, linked)
            '*' -> pair(STRONG, STRONG_MARKS, here, to, linked) ?: italic(here, starredEnd(here, to), linked)
            '~' -> pair(STRIKE, STRIKE_MARKS, here, to, linked)
            '<' -> discordForm(here, to) ?: autolink(here, to, linked)
            '\\' -> escaped(here, to)
            '!', '[' -> linkAt(here, to, linked)
            '`' -> code(here, to)
            '\n' -> Found(listOf(node(LINE_BREAK, here, here + 1)), here + 1)
            else -> addresses[here]?.takeIf { !linked && it.end <= to }?.let { address(here, it) }
        }

    private fun matcherAt(
        pattern: Pattern,
        here: Int,
        to: Int,
    ): Matcher? = pattern.matcher(content).region(here, to).takeIf { it.lookingAt() }

    private fun pair(
        kind: DescriptionNodeKind,
        pattern: Pattern,
        here: Int,
        to: Int,
        linked: Boolean,
    ): Found? {
        val found = matcherAt(pattern, here, to) ?: return null
        return Found(listOf(node(kind, here, found.end(), inlines(found.start(1), found.end(1), linked))), found.end())
    }

    // `_x_` only where the underscore does not stand against a word before it.
    private fun underscored(
        here: Int,
        from: Int,
        to: Int,
        linked: Boolean,
    ): Found? {
        if (here > from && word(content[here - 1])) return null
        return italic(here, underscoredEnd(here, to), linked)
    }

    private fun italic(
        here: Int,
        close: Int?,
        linked: Boolean,
    ): Found? = close?.let { Found(listOf(node(EMPHASIS, here, it + 1, inlines(here + 1, it, linked))), it + 1) }

    /*
     * The two italics and a link's target are scanned rather than matched: Java's engine recurses
     * once for each repeat of a group, so a long paragraph would run it out of stack. Each scan
     * reads what Discord's pattern reads.
     */

    // `*x*`: never against a space inside, and `**` within is bold, not the end.
    private fun starredEnd(
        here: Int,
        to: Int,
    ): Int? {
        if (here + 1 >= to || space(content[here + 1])) return null
        var at = here + 1
        while (at < to && !(at > here + 1 && lone(at, to, '*'))) at = starredTokenEnd(at, to) ?: return null
        return at.takeIf { it < to }
    }

    // Past `**`, an escape, a run of spaces and what follows it, or any other character.
    private fun starredTokenEnd(
        at: Int,
        to: Int,
    ): Int? =
        when {
            lone(at, to, '*') -> null
            content[at] == '*' -> at + 2
            content[at] == '\\' -> escapedEnd(at, to)
            space(content[at]) -> spacesEnd(at, to).takeIf { it < to && !lone(it, to, '*') }?.let { starredTokenEnd(it, to) }
            else -> at + 1
        }

    // `_x_`: closed by an underscore no word stands against, and `__` within is underlined.
    private fun underscoredEnd(
        here: Int,
        to: Int,
    ): Int? {
        var at = here + 1
        while (at < to && !(at > here + 1 && closesUnderscored(at, to))) at = underscoredTokenEnd(at, to) ?: return null
        return at.takeIf { it < to }
    }

    private fun closesUnderscored(
        at: Int,
        to: Int,
    ) = content[at] == '_' && (at + 1 >= to || !word(content[at + 1]))

    private fun underscoredTokenEnd(
        at: Int,
        to: Int,
    ): Int? =
        when (content[at]) {
            '_' -> (at + 2).takeIf { at + 1 < to && content[at + 1] == '_' }
            '\\' -> escapedEnd(at, to)
            else -> at + 1
        }

    // A mark standing alone, not doubled.
    private fun lone(
        at: Int,
        to: Int,
        mark: Char,
    ) = content[at] == mark && !(at + 1 < to && content[at + 1] == mark)

    private fun escapedEnd(
        at: Int,
        to: Int,
    ): Int? = if (at + 1 < to) at + 2 else null

    private fun discordForm(
        here: Int,
        to: Int,
    ): Found? {
        matcherAt(SERVER_EMOJI_FORM, here, to)?.let {
            val (moving, name, id) = (1..SERVER_EMOJI_PARTS).map(it::group)
            return found(node(SERVER_EMOJI, here, it.end()).copy(name = name, id = id, animated = moving == "a"))
        }
        matcherAt(MENTION_FORM, here, to)?.let {
            val kind =
                when (it.group(1)) {
                    "#" -> CHANNEL_MENTION
                    "@&" -> ROLE_MENTION
                    else -> USER_MENTION
                }
            return found(node(kind, here, it.end()).copy(id = it.group(2)))
        }
        return matcherAt(TIMESTAMP_FORM, here, to)?.let {
            found(
                node(TIMESTAMP, here, it.end()).copy(unix = it.group(1).toLong(), style = DescriptionTimeStyle.of(it.group(2))),
            )
        }
    }

    private fun autolink(
        here: Int,
        to: Int,
        linked: Boolean,
    ): Found? {
        if (linked) return null
        val found = matcherAt(AUTOLINK, here, to) ?: return null
        val written = found.group(1)
        val href = if (EMAIL_WHOLE.matcher(written).matches()) "mailto:$written" else written
        if (!safe(href)) return null
        return found(node(LINK, here, found.end(), listOf(node(TEXT, here + 1, found.end() - 1, text = written))).copy(href = href))
    }

    private fun escaped(
        here: Int,
        to: Int,
    ): Found? {
        if (here + 1 >= to) return null
        val next = content[here + 1]
        if (next == '\n') return found(node(LINE_BREAK, here, here + 2))
        if (next !in ESCAPABLE) return null
        return found(node(TEXT, here, here + 2, text = next.toString()))
    }

    private fun code(
        here: Int,
        to: Int,
    ): Found {
        var opened = here
        while (opened < to && content[opened] == '`') opened++
        val marks = opened - here
        var look = opened
        while (look < to) {
            val close = content.indexOf('`', look)
            if (close == -1 || close >= to) break
            var closed = close
            while (closed < to && content[closed] == '`') closed++
            if (closed - close == marks) {
                return found(node(CODE, here, closed, text = codeText(content.substring(opened, close))))
            }
            look = closed
        }
        return found(node(TEXT, here, opened, text = content.substring(here, opened)))
    }

    // One space either side of code is padding, unless the code is nothing but spaces.
    private fun codeText(written: String): String {
        val said = written.replace('\n', ' ')
        val padded = said.startsWith(" ") && said.endsWith(" ") && said.length > 2
        return if (padded && said.isNotBlank()) said.substring(1, said.length - 1) else said
    }

    private fun address(
        here: Int,
        address: Address,
    ): Found {
        val written = content.substring(here, address.end)
        val href =
            when {
                address.email -> "mailto:$written"
                written.startsWith("www.") -> "http://$written"
                else -> written
            }
        return found(node(LINK, here, address.end, listOf(node(TEXT, here, address.end, text = written))).copy(href = href))
    }

    private fun linkAt(
        here: Int,
        to: Int,
        linked: Boolean,
    ): Found? {
        val picture = content[here] == '!'
        val open = if (picture) here + 1 else here
        val opens = if (picture) open < to && content[open] == '[' else !linked
        val close = (if (opens) labelEnd(open, to) else null) ?: return null
        val target = targetAt(close + 1, to) ?: referenceAt(open, close, to) ?: return null
        val label = inlines(open + 1, close, linked = true)
        return if (picture) pictureOf(here, label, target) else linkOf(here, label, target)
    }

    private fun pictureOf(
        here: Int,
        label: List<DescriptionNode>,
        target: Target,
    ): Found {
        val alt = plainText(label)
        if (!safe(target.href, picture = true)) return found(node(TEXT, here, target.end, text = alt))
        return found(node(IMAGE, here, target.end, text = alt).copy(href = target.href, title = target.title))
    }

    // A link to where a link may not lead is only its words.
    private fun linkOf(
        here: Int,
        label: List<DescriptionNode>,
        target: Target,
    ): Found {
        if (!safe(target.href)) return Found(label, target.end)
        return found(node(LINK, here, target.end, label).copy(href = target.href, title = target.title))
    }

    // The bracket that closes the one at [open], skipping escapes, code and nested brackets.
    private fun labelEnd(
        open: Int,
        to: Int,
    ): Int? {
        var depth = 0
        var here = open
        while (here < to) {
            when (content[here]) {
                '\\' -> here++
                '[' -> depth++
                ']' -> if (--depth == 0) return here
                '`' -> {
                    val code = code(here, to)
                    if (code.nodes.single().kind == CODE) here = code.end - 1
                }
            }
            here++
        }
        return null
    }

    private class Target(
        val href: String,
        val title: String?,
        val end: Int,
    )

    // `(href)`, `(<href>)` or either with a title in quotes or brackets after a space.
    private fun targetAt(
        here: Int,
        to: Int,
    ): Target? {
        if (here >= to || content[here] != '(') return null
        val opened = spacesEnd(here + 1, to)
        val (href, after) = (if (opened < to && content[opened] == '<') bracketedHref(opened, to) else plainHref(opened, to)) ?: return null
        val titled = spacesEnd(after, to).takeIf { it > after }?.let { titleAt(it, to) }
        val close = spacesEnd(titled?.second ?: after, to)
        if (close >= to || content[close] != ')') return null
        return Target(unescaped(href), titled?.first?.let(::unescaped), close + 1)
    }

    private fun bracketedHref(
        open: Int,
        to: Int,
    ): Pair<String, Int>? {
        val close = (open + 1 until to).firstOrNull { content[it] in "<>\n" } ?: return null
        return if (content[close] == '>') content.substring(open + 1, close) to close + 1 else null
    }

    // A run of escapes and plain characters, with brackets one deep that close.
    private fun plainHref(
        from: Int,
        to: Int,
    ): Pair<String, Int>? {
        var at = from
        while (at < to) {
            at =
                when {
                    content[at] == '(' -> hrefTokensEnd(at + 1, to).takeIf { it < to && content[it] == ')' }?.plus(1) ?: return null
                    else -> hrefTokenEnd(at, to) ?: break
                }
        }
        return content.substring(from, at) to at
    }

    private fun hrefTokensEnd(
        from: Int,
        to: Int,
    ): Int {
        var at = from
        while (at < to) at = hrefTokenEnd(at, to) ?: break
        return at
    }

    // Past one escape or one character that is neither a space, a bracket nor a backslash.
    private fun hrefTokenEnd(
        at: Int,
        to: Int,
    ): Int? {
        val sign = content[at]
        return when {
            sign == '\\' -> (at + 2).takeIf { at + 1 < to && content[at + 1] !in LINE_ENDS }
            space(sign) || sign == '(' || sign == ')' -> null
            else -> at + 1
        }
    }

    private fun titleAt(
        open: Int,
        to: Int,
    ): Pair<String, Int>? {
        val close =
            when (content[open]) {
                '"' -> '"'
                '\'' -> '\''
                '(' -> ')'
                else -> return null
            }
        var at = open + 1
        while (at < to && content[at] != close) {
            if (content[at] == '\\') {
                if (at + 1 >= to || content[at + 1] in LINE_ENDS) return null
                at++
            } else if (close == ')' && content[at] == '(') {
                return null
            }
            at++
        }
        return if (at < to) content.substring(open + 1, at) to at + 1 else null
    }

    private fun spacesEnd(
        from: Int,
        to: Int,
    ): Int {
        var at = from
        while (at < to && space(content[at])) at++
        return at
    }

    // `[text][label]`, `[label][]` or `[label]`, where a definition names the label.
    private fun referenceAt(
        open: Int,
        close: Int,
        to: Int,
    ): Target? {
        val named = matcherAt(REFERENCE, close + 1, to)
        val label = named?.group(1)?.takeIf { it.isNotBlank() } ?: content.substring(open + 1, close)
        val definition = context.getDefinition(LinkReferenceDefinition::class.java, label) ?: return null
        return Target(definition.destination, definition.title, named?.end() ?: (close + 1))
    }

    private fun plainOf(
        from: Int,
        to: Int,
    ): List<DescriptionNode> {
        if (from >= to) return emptyList()
        val read = mutableListOf<DescriptionNode>()
        val clusters = GRAPHEME.matcher(content).region(from, to)
        var plain = from
        while (clusters.find()) {
            if (!presentedAsEmoji(clusters.group())) continue
            if (plain < clusters.start()) read += node(TEXT, plain, clusters.start(), text = content.substring(plain, clusters.start()))
            read += node(EMOJI, clusters.start(), clusters.end(), text = clusters.group())
            plain = clusters.end()
        }
        if (plain < to) read += node(TEXT, plain, to, text = content.substring(plain, to))
        return read
    }

    private fun found(node: DescriptionNode) = Found(listOf(node), node.end)

    private fun node(
        kind: DescriptionNodeKind,
        from: Int,
        to: Int,
        children: List<DescriptionNode> = emptyList(),
        text: String? = null,
    ) = DescriptionNode(kind, from, to, children, text)

    // Texts standing next to each other become one.
    private fun joined(nodes: List<DescriptionNode>): List<DescriptionNode> =
        nodes.fold(mutableListOf()) { read, node ->
            val last = read.lastOrNull()
            if (last != null && touching(last, node)) {
                read[read.lastIndex] = last.copy(end = node.end, text = last.text + node.text)
            } else {
                read += node
            }
            read
        }

    private fun touching(
        before: DescriptionNode,
        after: DescriptionNode,
    ) = before.kind == TEXT && after.kind == TEXT && before.end == after.start

    /** The nodes with their places moved from the content to the text the block came from. */
    fun placed(nodes: List<DescriptionNode>): List<DescriptionNode> =
        nodes.map { it.copy(start = at[it.start], end = at[it.end], children = placed(it.children)) }

    private class Address(
        val end: Int,
        val email: Boolean,
    )

    companion object {
        private val SPOILER_MARKS = Pattern.compile("\\|\\|([\\s\\S]+?)\\|\\|")
        private val UNDERLINE_MARKS = Pattern.compile("__([\\s\\S]+?)__(?!_)")
        private val STRONG_MARKS = Pattern.compile("\\*\\*([\\s\\S]+?)\\*\\*(?!\\*)")
        private val STRIKE_MARKS = Pattern.compile("~~([\\s\\S]+?)~~")
        private val SERVER_EMOJI_FORM = Pattern.compile("<(a?):(\\w{2,32}):(\\d{15,21})>")
        private val MENTION_FORM = Pattern.compile("<(@!?|@&|#)(\\d{15,21})>")
        private val TIMESTAMP_FORM = Pattern.compile("<t:(-?\\d{1,12})(?::([tTdDfFR]))?>")
        private const val EMAIL_SOURCE = "[a-zA-Z0-9._+-]+@[a-zA-Z0-9_-]+(?:\\.[a-zA-Z0-9_-]*[a-zA-Z0-9])+"
        private val EMAIL_WHOLE = Pattern.compile(EMAIL_SOURCE)
        private val AUTOLINK = Pattern.compile("<([a-zA-Z][a-zA-Z0-9+.-]{1,31}:[^\\s<>]*|$EMAIL_SOURCE)>")
        private val REFERENCE = Pattern.compile("\\[([^\\[\\]]*)]")
        private val URL = Regex("(?<![\\w@])(?:(?:https?|ftp)://|www\\.)(?:[a-zA-Z0-9-]+\\.?)+[^\\s<]*")
        private val EMAIL = Regex("(?<![a-zA-Z0-9._+-])$EMAIL_SOURCE(?![-_])")
        private val GRAPHEME = Pattern.compile("\\X")
        private const val ESCAPABLE = "!\"#$%&'()*+,-./:;<=>?@[]\\^_`{|}~"
        private val SAFE_SCHEME = Regex("^(?:https?|mailto|tel|ftp):", RegexOption.IGNORE_CASE)
        private val PICTURE_SCHEME = Regex("^https?:", RegexOption.IGNORE_CASE)
        private val NO_SCHEME = Regex("^[^:/?#]*(?:[/?#]|$)")
        private const val TRAILING = "?!.,:;*_'\"~"
        private const val VARIATION = 0xFE0F
        private const val SPACES = " \t\n\u000B\u000C\r"
        private const val LINE_ENDS = "\n\r"
        private const val SERVER_EMOJI_PARTS = 3
        private val WORDS = setOf(TEXT, CODE, EMOJI)

        // As Discord's patterns read them, which know only ASCII.
        private fun space(sign: Char) = sign in SPACES

        private fun word(sign: Char) = sign == '_' || sign in 'a'..'z' || sign in 'A'..'Z' || sign in '0'..'9'

        private fun addressesIn(content: String): Map<Int, Address> {
            val found = mutableMapOf<Int, Address>()
            for (url in URL.findAll(content)) found[url.range.first] = Address(url.range.first + trimmed(url.value).length, email = false)
            for (email in EMAIL.findAll(content)) found.putIfAbsent(email.range.first, Address(email.range.last + 1, email = true))
            return found
        }

        // An address written out ends before the punctuation after it, and before a bracket it did not open.
        private fun trimmed(url: String): String {
            var said = url
            while (said.last() in TRAILING || (said.last() == ')' && said.count { it == ')' } > said.count { it == '(' })) {
                said = said.dropLast(1)
            }
            return said
        }

        private fun safe(
            href: String,
            picture: Boolean = false,
        ): Boolean = (if (picture) PICTURE_SCHEME else SAFE_SCHEME).containsMatchIn(href) || NO_SCHEME.containsMatchIn(href)

        private fun unescaped(text: String) = text.replace(Regex("\\\\([!-/:-@\\[-`{-~])"), "$1")

        private fun plainText(nodes: List<DescriptionNode>): String =
            nodes.joinToString("") { node -> node.text?.takeIf { node.kind in WORDS } ?: plainText(node.children) }

        /** A character drawn as an emoji by default, or written to be drawn as one. */
        fun presentedAsEmoji(cluster: String): Boolean {
            val first = cluster.codePointAt(0)
            return Character.isEmojiPresentation(first) || (Character.isEmoji(first) && cluster.codePoints().anyMatch { it == VARIATION })
        }
    }
}
