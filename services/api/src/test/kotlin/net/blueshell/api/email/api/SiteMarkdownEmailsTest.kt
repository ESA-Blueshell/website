package net.blueshell.api.email.api

import net.blueshell.api.email.domain.EmailBodyMarkdown
import net.blueshell.api.shared.discord.DiscordMentionNames
import net.blueshell.api.shared.discord.MentionNames
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SiteMarkdownEmailsTest {
    @Test
    fun `an editor's message renders into the email body with its pictures and names, and unknown mentions as text`() {
        val known = SiteMarkdownEmails { MentionNames(users = mapOf("123456789012345611" to "Anna")) }
        val body = EmailBodyMarkdown.render(known.forEmail("**Hi** <@123456789012345611> 🎉 <:gg:123456789012345678>"))

        assertThat(body).contains("<strong", "@Anna", "1f389.png", "alt=\":gg:\"")
        val offline = SiteMarkdownEmails(DiscordMentionNames { null })
        assertThat(offline.forEmail("<@123456789012345611>")).isEqualTo("@unknown-user")
    }
}
