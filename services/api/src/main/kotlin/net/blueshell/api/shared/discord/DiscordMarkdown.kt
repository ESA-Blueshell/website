package net.blueshell.api.shared.discord

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A description written in Discord's markdown (architecture ADR-010), translated to CommonMark for
 * a place that reads only that: Google Calendar, which renders HTML, and a link preview, which
 * reads plain words. A spoiler's text never leaves. The frontend's discordMarkdown.ts renders the
 * same rules for the site; change one, change the other.
 */
object DiscordMarkdown {
    private val ZONE = ZoneId.of("Europe/Amsterdam")
    private val DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
    private val WEEKDAY_DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
    private val SHORT_DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH)

    private val MENTION = Regex("<(@!?|@&|#)(\\d{15,21})>")
    private val SERVER_EMOJI = Regex("<a?:(\\w{2,32}):\\d{15,21}>")
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

    /** [text] as CommonMark; [html] keeps underline and small print as inline HTML, else only their words. */
    fun toCommonMark(
        text: String,
        names: MentionNames,
        html: Boolean,
    ): String {
        val read = mutableListOf<String>()
        var fenced = false
        var listed = false
        val lines = text.split("\n")
        for ((at, line) in lines.withIndex()) {
            if (FENCE.containsMatchIn(line)) fenced = !fenced
            if (fenced || FENCE.containsMatchIn(line)) {
                read.add(line)
                continue
            }
            if (QUOTE_REST.containsMatchIn(line)) {
                val rest = listOf(line.replace(QUOTE_REST, "")) + lines.drop(at + 1)
                rest.mapTo(read) { "> ${inline(it, names, html)}" }
                break
            }
            if (listed && line.isNotBlank() && !line.first().isWhitespace() && !ITEM.containsMatchIn(line)) read.add("")
            listed = ITEM.containsMatchIn(line) || (listed && line.isNotBlank() && line.first().isWhitespace())
            val small = SUBTEXT.find(line)
            read.add(
                when {
                    small == null -> inline(line, names, html)
                    html -> "<small>${inline(small.groupValues[1], names, html)}</small>"
                    else -> inline(small.groupValues[1], names, html)
                },
            )
        }
        return read.joinToString("\n")
    }

    /* Inline rules outside code spans, which keep every character as written. */
    private fun inline(
        line: String,
        names: MentionNames,
        html: Boolean,
    ): String {
        val out = StringBuilder()
        var from = 0
        for (code in CODE.findAll(line)) {
            out.append(words(line.substring(from, code.range.first), names, html)).append(code.value)
            from = code.range.last + 1
        }
        return out.append(words(line.substring(from), names, html)).toString()
    }

    private fun words(
        text: String,
        names: MentionNames,
        html: Boolean,
    ): String =
        text
            .replace(SPOILER, SPOILER_SAID)
            .replace(SERVER_EMOJI) { ":${it.groupValues[1]}:" }
            .replace(MENTION) { escaped(nameOf(it.groupValues[1], it.groupValues[2], names)) }
            .replace(TIMESTAMP) { momentOf(it.groupValues[1].toLong(), it.groupValues[2]) }
            .replace(UNDERLINE) { if (html) "<u>${it.groupValues[1]}</u>" else it.groupValues[1] }
            .replace(BOLD) { boldOf(it.groupValues[1]) }
            .replace(TILDE, "\\\\~")

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

    /* Discord closes bold however it is flanked; CommonMark does not, so the spaces step outside. */
    private fun boldOf(inside: String): String {
        val said = inside.trim()
        if (said.isEmpty()) return "**$inside**"
        return "${inside.takeWhile { it.isWhitespace() }}**$said**${inside.takeLastWhile { it.isWhitespace() }}"
    }

    /* In Amsterdam time and English: a calendar and a preview are read by no reader in particular. */
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
