package net.blueshell.api.esports.domain

import java.time.LocalDate

/** What a team write says about a team. Its banner is the fielding's, so it is not here. */
data class TeamInput(
    val name: String,
    val icon: String? = null,
)

/** What a season write says about a season. */
data class SeasonInput(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
)
