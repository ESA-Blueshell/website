package net.blueshell.api.shared.discord

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A description written in Discord's markdown (architecture ADR-010), translated to CommonMark for
 * a place that reads only that: Google Calendar, which renders HTML, a link preview, which reads
 * plain words, and an email. A spoiler's text leaves only in an email, hidden until selected. The
 * frontend's discordMarkdown.ts and [DescriptionReader] read the same rules for the site; change
 * one, change the others.
 */
object DiscordMarkdown {
    private val ZONE = ZoneId.of("Europe/Amsterdam")
    private val DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
    private val WEEKDAY_DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
    private val SHORT_DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH)

    private val MENTION = Regex("<(@!?|@&|#)(\\d{15,21})>")
    private val SERVER_EMOJI = Regex("<(a?):(\\w{2,32}):(\\d{15,21})>")
    private val STRIKE = Regex("(?<!~)~~(?!~)([\\s\\S]+?)(?<!~)~~(?!~)")

    // A keycap, a flag, or a pictograph with what modifies or joins it: variation selector, skin
    // tone, and the tag characters that spell a flag such as England's.
    private const val MODIFIER = "[\\x{FE0F}\\x{1F3FB}-\\x{1F3FF}\\x{E0020}-\\x{E007F}]"
    private val UNICODE_EMOJI =
        Regex(
            "[0-9#*]\\x{FE0F}?\\x{20E3}|[\\x{1F1E6}-\\x{1F1FF}]{2}|" +
                "\\p{IsExtended_Pictographic}$MODIFIER*(?:\\x{200D}\\p{IsExtended_Pictographic}$MODIFIER*)*",
        )

    // Editor-typed HTML becomes text; mentions, emoji, times and autolinks open with `<` too.
    private val TYPED_TAG = Regex("<(?![@#]|a?:\\w|t:|https?://|mailto:)")

    // A link's address, which a picture would break.
    private val ADDRESS = Regex("\\]\\([^)\\s]*\\)|<https?://[^>\\s]*>|https?://\\S+")
    private const val EMOJI_SIZE = 22
    private const val TWEMOJI = "https://cdn.jsdelivr.net/gh/jdecked/twemoji@15.1.0/assets/72x72"
    private val TIMESTAMP = Regex("<t:(-?\\d{1,12})(?::([tTdDfFR]))?>")
    private val SPOILER = Regex("\\|\\|([\\s\\S]+?)\\|\\|")
    private val UNDERLINE = Regex("(?<!_)__(?!_)([\\s\\S]+?)(?<!_)__(?!_)")
    private val BOLD = Regex("(?<!\\*)\\*\\*(?!\\*)([\\s\\S]+?)\\*\\*(?!\\*)")
    private val TILDE = Regex("(?<!~)~(?!~)")
    private val CODE = Regex("(`+)[\\s\\S]*?\\1")
    private val FENCE = Regex("^\\s{0,3}(```|~~~)")
    private val ITEM = Regex("^\\s*(?:[-*+]|\\d+[.)])\\s")
    private val QUOTE_REST = Regex("^>>> ?")
    private val SUBTEXT = Regex("^-# (.*)$")
    private val MARKDOWN_SIGNS = Regex("([\\\\`*_~|<>\\[\\]#])")

    /** Stands in for a spoiler, whose text stays in Discord and on the site. */
    const val SPOILER_SAID = "(spoiler)"

    /** The members, roles and channels [text] mentions. */
    fun mentionsIn(text: String): MentionIds {
        val found = MENTION.findAll(text).map { it.groupValues[1] to it.groupValues[2] }.toList()
        return MentionIds(
            users = found.filter { it.first.startsWith("@") && it.first != "@&" }.map { it.second }.toSet(),
            roles = found.filter { it.first == "@&" }.map { it.second }.toSet(),
            channels = found.filter { it.first == "#" }.map { it.second }.toSet(),
        )
    }

    /**
     * [text] as CommonMark; [html] keeps underline and small print as inline HTML, else only their
     * words. [email] also keeps strike and a spoiler's text, hidden until selected, and draws emoji
     * as PNG pictures a mail client shows, with their names as the text where a picture does not load.
     */
    fun toCommonMark(
        text: String,
        names: MentionNames,
        html: Boolean,
        email: Boolean = false,
    ): String {
        require(html || !email) { "An email is HTML." }
        val lines = text.split("\n")
        val quoted = quoteStartOf(lines)
        val read = mutableListOf<String>()
        var fenced = false
        var listed = false
        for (line in lines.take(quoted)) {
            val fence = FENCE.containsMatchIn(line)
            if (fence) fenced = !fenced
            if (fenced || fence) {
                read.add(line)
            } else {
                if (listed && unmarked(line)) read.add("")
                listed = ITEM.containsMatchIn(line) || (listed && indented(line))
                read.add(lineOf(line, names, html, email))
            }
        }
        lines.drop(quoted).mapIndexedTo(read) { at, line ->
            "> ${inline(if (at == 0) line.replace(QUOTE_REST, "") else line, names, html, email)}"
        }
        return read.joinToString("\n")
    }

    // The line `>>>` quotes the rest from, outside code, or past the end where there is none.
    private fun quoteStartOf(lines: List<String>): Int {
        var fenced = false
        lines.forEachIndexed { at, line ->
            if (FENCE.containsMatchIn(line)) {
                fenced = !fenced
            } else if (!fenced && QUOTE_REST.containsMatchIn(line)) {
                return at
            }
        }
        return lines.size
    }

    // A line that is neither an item nor indented under one, which ends a list in Discord.
    private fun unmarked(line: String) = line.isNotBlank() && !line.first().isWhitespace() && !ITEM.containsMatchIn(line)

    private fun indented(line: String) = line.isNotBlank() && line.first().isWhitespace()

    private fun lineOf(
        line: String,
        names: MentionNames,
        html: Boolean,
        email: Boolean,
    ): String {
        val small = SUBTEXT.find(line) ?: return inline(line, names, html, email)
        val said = inline(small.groupValues[1], names, html, email)
        return if (html) "<small>$said</small>" else said
    }

    // Inline rules outside code spans, which keep every character as written.
    private fun inline(
        line: String,
        names: MentionNames,
        html: Boolean,
        email: Boolean,
    ): String {
        val out = StringBuilder()
        var from = 0
        for (code in CODE.findAll(line)) {
            out.append(words(line.substring(from, code.range.first), names, html, email)).append(code.value)
            from = code.range.last + 1
        }
        return out.append(words(line.substring(from), names, html, email)).toString()
    }

    private fun words(
        text: String,
        names: MentionNames,
        html: Boolean,
        email: Boolean,
    ): String =
        (if (email) emailOnly(text) else text.replace(SPOILER, SPOILER_SAID))
            .replace(SERVER_EMOJI) { if (email) serverEmojiOf(it) else ":${it.groupValues[2]}:" }
            .replace(MENTION) { escaped(nameOf(it.groupValues[1], it.groupValues[2], names)) }
            .replace(TIMESTAMP) { momentOf(it.groupValues[1].toLong(), it.groupValues[2]) }
            .replace(UNDERLINE) { if (html) "<u>${it.groupValues[1]}</u>" else it.groupValues[1] }
            .replace(BOLD) { boldOf(it.groupValues[1]) }
            .replace(TILDE, "\\\\~")

    // Typed HTML escaped before any tag of ours goes in; a picture before the spoiler and strike
    // that wrap it.
    private fun emailOnly(text: String): String =
        picturesOutsideAddresses(text.replace(TYPED_TAG, "&lt;"))
            .replace(SPOILER) {
                "<span style=\"background-color:#1E1F22; color:#1E1F22; border-radius:3px;\">${it.groupValues[1]}</span>"
            }.replace(STRIKE) { "<s>${it.groupValues[1]}</s>" }

    private fun serverEmojiOf(found: MatchResult): String {
        val (animated, name, id) = found.destructured
        val kind = if (animated.isEmpty()) "png" else "gif"
        return picture("https://cdn.discordapp.com/emojis/$id.$kind?size=48", ":$name:")
    }

    private fun picturesOutsideAddresses(text: String): String {
        val out = StringBuilder()
        var from = 0
        for (address in ADDRESS.findAll(text)) {
            out.append(text.substring(from, address.range.first).replace(UNICODE_EMOJI) { unicodeEmojiOf(it.value) })
            out.append(address.value)
            from = address.range.last + 1
        }
        return out.append(text.substring(from).replace(UNICODE_EMOJI) { unicodeEmojiOf(it.value) }).toString()
    }

    // Twemoji names a picture by its code points, dropping the variation selector outside a joined sequence.
    private fun unicodeEmojiOf(emoji: String): String {
        val points = emoji.codePoints().toArray()
        // A symbol such as ™ or ▶ is text, as in Discord, unless something follows that makes it a picture.
        if (points.size == 1 && !Character.isEmojiPresentation(points[0])) return emoji
        val joined = emoji.contains('\u200D')
        val named = points.filter { joined || it != 0xFE0F }.joinToString("-") { Integer.toHexString(it) }
        return picture("$TWEMOJI/$named.png", emoji)
    }

    private fun picture(
        src: String,
        alt: String,
    ) = "<img src=\"$src\" alt=\"$alt\" width=\"$EMOJI_SIZE\" height=\"$EMOJI_SIZE\" style=\"vertical-align:-4px; border:0;\">"

    private fun nameOf(
        sign: String,
        id: String,
        names: MentionNames,
    ): String =
        when (sign) {
            "#" -> names.channels[id]?.let { "#$it" } ?: "#unknown"
            "@&" -> names.roles[id]?.let { if (it.startsWith("@")) it else "@$it" } ?: "@deleted-role"
            else -> names.users[id]?.let { "@$it" } ?: "@unknown-user"
        }

    private fun escaped(said: String) = said.replace(MARKDOWN_SIGNS, "\\\\$1")

    // Discord closes bold however it is flanked; CommonMark does not, so the spaces step outside.
    private fun boldOf(inside: String): String {
        val said = inside.trim()
        if (said.isEmpty()) return "**$inside**"
        return "${inside.takeWhile { it.isWhitespace() }}**$said**${inside.takeLastWhile { it.isWhitespace() }}"
    }

    // In Amsterdam time and English: a calendar and a preview are read by no reader in particular.
    private fun momentOf(
        unix: Long,
        style: String,
    ): String {
        val at = Instant.ofEpochSecond(unix).atZone(ZONE)
        return when (style) {
            "t" -> TIME.format(at)
            "T" -> SECONDS.format(at)
            "d" -> SHORT_DAY.format(at)
            "D" -> DAY.format(at)
            "F" -> "${WEEKDAY_DAY.format(at)} ${TIME.format(at)}"
            else -> "${DAY.format(at)} ${TIME.format(at)}"
        }
    }
}
