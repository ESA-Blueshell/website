package net.blueshell.api.esports.api

/** Somebody went on or off a team's line-up, or a line-up was carried into a season. */
data class RosterChanged(
    val teamId: Long,
    val userIds: Set<Long>,
)
