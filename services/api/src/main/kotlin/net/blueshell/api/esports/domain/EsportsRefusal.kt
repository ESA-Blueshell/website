package net.blueshell.api.esports.domain

import org.springframework.http.HttpStatus

// A code and the facts, never the sentence: `esports/refusals.ts` writes that. See ADR-026.
sealed class EsportsRefusal(
    val status: HttpStatus,
    val code: String,
    val summary: String,
    val facts: Map<String, Any>,
) : RuntimeException(summary)

class GameHoldsHistory(
    gameName: String,
    teams: Long,
    players: Long,
) : EsportsRefusal(
        HttpStatus.CONFLICT,
        "GameHoldsHistory",
        "That game cannot be removed.",
        mapOf("gameName" to gameName, "teams" to teams, "players" to players),
    )

class GameFieldedInSeason(
    gameName: String,
    teams: Int,
) : EsportsRefusal(
        HttpStatus.CONFLICT,
        "GameFieldedInSeason",
        "That game cannot be taken out of the season.",
        mapOf("gameName" to gameName, "teams" to teams),
    )

class SeasonDatesOverlap(
    seasonName: String,
) : EsportsRefusal(
        HttpStatus.BAD_REQUEST,
        "SeasonDatesOverlap",
        "Those dates overlap another season.",
        mapOf("seasonName" to seasonName),
    )

class SeasonEndsBeforeStart :
    EsportsRefusal(
        HttpStatus.BAD_REQUEST,
        "SeasonEndsBeforeStart",
        "A season cannot end before it starts.",
        emptyMap(),
    )
