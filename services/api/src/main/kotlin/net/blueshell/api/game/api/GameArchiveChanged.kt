package net.blueshell.api.game.api

/** A game archived, or brought back, with the Discord channels it lives in. */
data class GameArchiveChanged(
    val code: String,
    val archived: Boolean,
    val channelIds: List<String>,
)
