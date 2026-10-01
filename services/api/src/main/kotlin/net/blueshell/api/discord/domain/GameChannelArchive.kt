package net.blueshell.api.discord.domain

import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.game.api.GameArchiveChanged
import net.blueshell.api.shared.event.AfterCommitListener
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** A game archived takes its channels into the archive category, and brought back takes them out again. */
@Component
class GameChannelArchive(
    private val channels: DiscordChannelKeeper,
) {
    @AfterCommitListener
    fun onGameArchiveChanged(event: GameArchiveChanged) {
        if (event.channelIds.isEmpty()) return
        if (!channels.available()) return log.warn("[discord] {} was archived or restored with no bot to move its channels", event.code)
        if (event.archived) channels.archive(event.channelIds) else channels.restore(event.channelIds)
    }

    private companion object {
        val log = LoggerFactory.getLogger(GameChannelArchive::class.java)
    }
}
