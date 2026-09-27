package net.blueshell.api.discord.domain

import io.swagger.v3.oas.annotations.media.Schema

/** Which of the server's categories a game's channels are picked from: casual play or its esports. */
@Schema(enumAsRef = true)
enum class GameChannelCategory {
    GAMES,
    ESPORTS,
}
