package net.blueshell.api.esports.domain

import org.springframework.http.HttpStatus

// A code and the facts, never the sentence: `esports/refusals.ts` writes that. See ADR-026.
sealed class EsportsRefusal(
    val status: HttpStatus,
    val code: String,
    val summary: String,
    val facts: Map<String, Any>,
) : RuntimeException(summary)

class UnknownGameCode(gameCode: String) : EsportsRefusal(
    HttpStatus.BAD_REQUEST,
    "UnknownGameCode",
    "No game has that code.",
    mapOf("gameCode" to gameCode),
)

class GameNameBlank : EsportsRefusal(
    HttpStatus.BAD_REQUEST,
    "GameNameBlank",
    "A game needs a name.",
    emptyMap(),
)

class GameNameUnusable(given: String) : EsportsRefusal(
    HttpStatus.BAD_REQUEST,
    "GameNameUnusable",
    "That name has nothing a code can be made from.",
    mapOf("given" to given),
)

class GameAlreadyExists(gameName: String) : EsportsRefusal(
    HttpStatus.CONFLICT,
    "GameAlreadyExists",
    "That game already exists.",
    mapOf("gameName" to gameName),
)

class GameHoldsHistory(gameName: String, teams: Long, players: Long) : EsportsRefusal(
    HttpStatus.CONFLICT,
    "GameHoldsHistory",
    "That game cannot be removed.",
    mapOf("gameName" to gameName, "teams" to teams, "players" to players),
)

class GameFieldedInSeason(gameName: String, teams: Int) : EsportsRefusal(
    HttpStatus.CONFLICT,
    "GameFieldedInSeason",
    "That game cannot be taken out of the season.",
    mapOf("gameName" to gameName, "teams" to teams),
)

class GameAddressBlank : EsportsRefusal(
    HttpStatus.BAD_REQUEST,
    "GameAddressBlank",
    "A game needs an address.",
    emptyMap(),
)

class AddressReserved(address: String) : EsportsRefusal(
    HttpStatus.CONFLICT,
    "AddressReserved",
    "That address belongs to the esports listing.",
    mapOf("address" to address),
)

class AddressTaken(gameName: String, address: String) : EsportsRefusal(
    HttpStatus.CONFLICT,
    "AddressTaken",
    "That address is already used by another game.",
    mapOf("gameName" to gameName, "address" to address),
)

class SeasonDatesOverlap(seasonName: String) : EsportsRefusal(
    HttpStatus.BAD_REQUEST,
    "SeasonDatesOverlap",
    "Those dates overlap another season.",
    mapOf("seasonName" to seasonName),
)

class SeasonEndsBeforeStart : EsportsRefusal(
    HttpStatus.BAD_REQUEST,
    "SeasonEndsBeforeStart",
    "A season cannot end before it starts.",
    emptyMap(),
)

class PictureNotStored : EsportsRefusal(
    HttpStatus.BAD_REQUEST,
    "PictureNotStored",
    "That picture is not in storage.",
    emptyMap(),
)
