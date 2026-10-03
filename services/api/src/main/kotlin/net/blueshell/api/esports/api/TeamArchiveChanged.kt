package net.blueshell.api.esports.api

/** A team stopped playing, or plays again. */
data class TeamArchiveChanged(
    val teamId: Long,
    val archived: Boolean,
)
