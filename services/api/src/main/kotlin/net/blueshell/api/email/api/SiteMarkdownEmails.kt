package net.blueshell.api.email.api

import net.blueshell.api.shared.discord.DiscordMarkdown
import net.blueshell.api.shared.discord.DiscordMentionNames
import net.blueshell.api.shared.discord.MentionNames
import org.springframework.stereotype.Service

/**
 * A message written in the site's editor, in Discord's markdown, made ready for an email: mentions
 * named, times in Europe/Amsterdam, emoji as pictures. The preview and the send both render what
 * this answers, so they cannot differ. Emails the site builds itself are CommonMark and skip it.
 */
@Service
class SiteMarkdownEmails(
    private val names: DiscordMentionNames,
) {
    fun forEmail(written: String): String =
        DiscordMarkdown.toCommonMark(
            written,
            names.named(DiscordMarkdown.mentionsIn(written)) ?: MentionNames(),
            html = true,
            email = true,
        )
}
