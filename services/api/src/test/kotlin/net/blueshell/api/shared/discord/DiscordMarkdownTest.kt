package net.blueshell.api.shared.discord

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DiscordMarkdownTest {
    private val names =
        MentionNames(
            users = mapOf("123456789012345611" to "Anna_B"),
            roles = mapOf("223456789012345901" to "Gamers", "324285132133629963" to "@everyone"),
            channels = mapOf("323456789012345602" to "events-info"),
        )

    private fun html(text: String) = DiscordMarkdown.toCommonMark(text, names, html = true)

    private fun plain(text: String) = DiscordMarkdown.toCommonMark(text, names, html = false)

    @Test
    fun `finds the members, roles and channels a description mentions`() {
        assertThat(DiscordMarkdown.mentionsIn("<@123456789012345611> <@!123456789012345612> <@&223456789012345901> <#323456789012345602>"))
            .isEqualTo(
                MentionIds(
                    users = setOf("123456789012345611", "123456789012345612"),
                    roles = setOf("223456789012345901"),
                    channels = setOf("323456789012345602"),
                ),
            )
    }

    @Test
    fun `names mentions as Discord shows them, and what the server lacks as Discord does`() {
        assertThat(plain("<@123456789012345611> <@&223456789012345901> <@&324285132133629963> <#323456789012345602>"))
            .isEqualTo("@Anna\\_B @Gamers @everyone \\#events-info")
        assertThat(plain("<@123456789012345699> <@&223456789012345999> <#323456789012345699>"))
            .isEqualTo("@unknown-user @deleted-role \\#unknown")
    }

    @Test
    fun `never lets a spoiler's text out`() {
        assertThat(plain("the end: ||he wins|| and more")).isEqualTo("the end: ${DiscordMarkdown.SPOILER_SAID} and more")
    }

    @Test
    fun `writes a server emoji by its name`() {
        assertThat(plain("gg <:POGGERS:657733730491826186> <a:party:123456789012345678>")).isEqualTo("gg :POGGERS: :party:")
    }

    @Test
    fun `writes a timestamp as a date in Amsterdam time, in its style`() {
        val at = 1790000000L // 21 September 2026, 16:13:20 in Amsterdam
        assertThat(plain("<t:$at:t> <t:$at:T> <t:$at:d> <t:$at:D>")).isEqualTo("16:13 16:13:20 21/09/2026 21 September 2026")
        assertThat(plain("<t:$at:F>")).isEqualTo("Monday, 21 September 2026 16:13")
        assertThat(plain("<t:$at> <t:$at:R>")).isEqualTo("21 September 2026 16:13 21 September 2026 16:13")
    }

    @Test
    fun `underlines and sets subtext small in HTML, and keeps only their words in plain text`() {
        assertThat(html("__under__ and _slanted_\n-# small **print**")).isEqualTo("<u>under</u> and _slanted_\n<small>small **print**</small>")
        assertThat(plain("__under__\n-# small")).isEqualTo("under\nsmall")
    }

    @Test
    fun `closes bold however it is flanked, and escapes a single tilde`() {
        assertThat(plain("**Sign ups required! ** now")).isEqualTo("**Sign ups required!**  now")
        assertThat(plain("** ** and ~kept~ but ~~gone~~")).isEqualTo("** ** and \\~kept\\~ but ~~gone~~")
    }

    @Test
    fun `quotes everything after three angle brackets, and ends a list at a line that is not an item`() {
        assertThat(plain("intro\n>>> one\ntwo")).isEqualTo("intro\n> one\n> two")
        assertThat(plain("- one\n  under\nafter")).isEqualTo("- one\n  under\n\nafter")
    }

    @Test
    fun `leaves code as written`() {
        assertThat(plain("`||x|| <@123456789012345611>` and ||y||")).isEqualTo("`||x|| <@123456789012345611>` and ${DiscordMarkdown.SPOILER_SAID}")
        assertThat(plain("```\n||x||\n>>> no\n```\n||y||")).isEqualTo("```\n||x||\n>>> no\n```\n${DiscordMarkdown.SPOILER_SAID}")
    }
}
