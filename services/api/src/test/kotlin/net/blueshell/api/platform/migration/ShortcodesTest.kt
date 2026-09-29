package net.blueshell.api.platform.migration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ShortcodesTest {
    private val names = Shortcodes.names()

    private fun rewritten(text: String) = Shortcodes.rewrite(text, names)

    @Test
    fun `reads the names the site expanded, from the committed table`() {
        assertThat(names)
            .hasSize(1570)
            .containsEntry("smile", "😄")
            .containsEntry("+1", "👍")
            .containsEntry("100", "💯")
        assertThat(names).doesNotContainKey("thumbsup")
    }

    @Test
    fun `writes each shortcode it knows as its emoji, and counts them`() {
        assertThat(rewritten("Bring snacks :smile: and :+1:!")).isEqualTo("Bring snacks 😄 and 👍!" to 2)
        assertThat(rewritten("a:smile:b\n:tada::tada:")).isEqualTo("a😄b\n🎉🎉" to 3)
    }

    @Test
    fun `leaves what it does not know, and a time, as it is`() {
        assertThat(rewritten("Doors at 12:30:45, :nope: and :Smile:")).isEqualTo("Doors at 12:30:45, :nope: and :Smile:" to 0)
    }

    @Test
    fun `leaves code, addresses, link targets, escapes and anything in angle brackets as they are`() {
        val kept =
            listOf(
                "`:smile:`",
                "``a ` :smile:``",
                "``a ``` :smile:``",
                "see https://example.com/:smile:",
                "see www.example.com/:smile:",
                "<:smile:657733730491826186> <a:smile:1> <t:1790000000:R> <@123> <https://x/:smile:>",
                "\\:smile:",
                "```\n:smile:\n```",
                "~~~~\n```\n:smile:\n~~~~",
            )

        for (text in kept) assertThat(rewritten(text)).`as`(text).isEqualTo(text to 0)
    }

    @Test
    fun `writes shortcodes around what it leaves`() {
        assertThat(rewritten("[:smile: here](https://x/:smile:) `:tada:` :tada:"))
            .isEqualTo("[😄 here](https://x/:smile:) `:tada:` 🎉" to 2)
        assertThat(rewritten("```\n:smile:\n```\n:smile:")).isEqualTo("```\n:smile:\n```\n😄" to 1)
        assertThat(rewritten("a < b :smile: > c, and `unclosed :smile:")).isEqualTo("a < b 😄 > c, and `unclosed 😄" to 2)
    }
}
