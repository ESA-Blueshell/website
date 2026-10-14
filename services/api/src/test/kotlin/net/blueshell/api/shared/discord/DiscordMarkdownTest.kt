package net.blueshell.api.shared.discord

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

    private fun email(text: String) = DiscordMarkdown.toCommonMark(text, names, html = true, email = true)

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
        assertThat(html("__under__ and _slanted_\n-# small **print**"))
            .isEqualTo("<u>under</u> and _slanted_\n<small>small **print**</small>")
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
        assertThat(plain("`||x|| <@123456789012345611>` and ||y||"))
            .isEqualTo("`||x|| <@123456789012345611>` and ${DiscordMarkdown.SPOILER_SAID}")
        assertThat(plain("```\n||x||\n>>> no\n```\n||y||")).isEqualTo("```\n||x||\n>>> no\n```\n${DiscordMarkdown.SPOILER_SAID}")
    }

    @Test
    fun `draws server and unicode emoji as PNG pictures an email shows, named where a picture does not load`() {
        assertThat(email("hi <:blueshell:123456789012345678> and <a:wave:123456789012345679>"))
            .contains("""<img src="https://cdn.discordapp.com/emojis/123456789012345678.png?size=48" alt=":blueshell:"""")
            .contains("""<img src="https://cdn.discordapp.com/emojis/123456789012345679.gif?size=48" alt=":wave:"""")
        assertThat(email("well done 👍🏽 ❤️ 👨‍👩‍👧 🇳🇱"))
            .contains("assets/72x72/1f44d-1f3fd.png\" alt=\"👍🏽\"")
            .contains("assets/72x72/2764.png\" alt=\"❤️\"")
            .contains("assets/72x72/1f468-200d-1f469-200d-1f467.png")
            .contains("assets/72x72/1f1f3-1f1f1.png")
            .doesNotContain(".svg")
        assertThat(email("© 2026, 3 # items")).isEqualTo("© 2026, 3 # items")
    }

    @Test
    fun `keeps a spoiler hidden until selected, strike, underline and small print, and leaves code alone`() {
        val said = email("-# fine print\n__under__ ~~gone~~ ||secret|| `||kept||`")
        assertThat(said).contains("<small>fine print</small>", "<u>under</u>", "<s>gone</s>", "`||kept||`")
        assertThat(said).contains(">secret</span>").contains("background-color:#1E1F22")
    }

    @Test
    fun `names mentions and dates times in Amsterdam in an email too, and keeps the old look elsewhere`() {
        assertThat(email("<@123456789012345611> on <t:1790000000:F>")).isEqualTo("@Anna\\_B on Monday, 21 September 2026 16:13")
        assertThat(html("~~old~~ <:blueshell:123456789012345678>")).isEqualTo("~~old~~ :blueshell:")
    }

    @Test
    fun `draws keycaps and tag-spelled flags, and leaves a text symbol such as a trademark as text`() {
        assertThat(email("1\uFE0F\u20E3 #\uFE0F\u20E3 *\uFE0F\u20E3"))
            .contains("72x72/31-20e3.png", "72x72/23-20e3.png", "72x72/2a-20e3.png")
        assertThat(email("\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F"))
            .contains("72x72/1f3f4-e0067-e0062-e0065-e006e-e0067-e007f.png")
            .startsWith("<img")
            .endsWith(">")
        assertThat(email("Blueshell™ ▶ go ↔")).isEqualTo("Blueshell™ ▶ go ↔")
        assertThat(email("▶\uFE0F")).contains("72x72/25b6.png")
    }

    @Test
    fun `leaves a link's address whole when it holds an emoji`() {
        val said = email("[docs](https://x.org/🎉) https://y.org/🎉 <https://z.org/🎉> 🎉")
        assertThat(said).startsWith("[docs](https://x.org/🎉) https://y.org/🎉 <https://z.org/🎉> <img")
    }

    @Test
    fun `writes HTML an editor types as text in an email, and keeps mentions, emoji and links`() {
        assertThat(email("<b>x</b> </td><style>td{}</style> <!-- c -->"))
            .isEqualTo("&lt;b>x&lt;/b> &lt;/td>&lt;style>td{}&lt;/style> &lt;!-- c -->")
        assertThat(email("<https://x.org> <@123456789012345611> <:gg:123456789012345678>"))
            .startsWith("<https://x.org> @Anna\\_B <img")
    }

    @Test
    fun `keeps unicode emoji as text outside an email`() {
        assertThat(html("🎉 ™")).isEqualTo("🎉 ™")
    }

    @Test
    fun `refuses an email that is not HTML`() {
        assertThatThrownBy { DiscordMarkdown.toCommonMark("x", names, html = false, email = true) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
