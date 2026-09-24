package net.blueshell.api.game.api

import org.springframework.http.HttpStatus

// A code and the facts, never the sentence: `games/refusals.ts` writes that. See ADR-026.
sealed class GameRefusal(
    val status: HttpStatus,
    val code: String,
    val summary: String,
    val facts: Map<String, Any>,
) : RuntimeException(summary)

class UnknownGameCode(
    gameCode: String,
) : GameRefusal(HttpStatus.BAD_REQUEST, "UnknownGameCode", "No game has that code.", mapOf("gameCode" to gameCode))

class GameNameBlank : GameRefusal(HttpStatus.BAD_REQUEST, "GameNameBlank", "A game needs a name.", emptyMap())

class GameNameUnusable(
    given: String,
) : GameRefusal(
        HttpStatus.BAD_REQUEST,
        "GameNameUnusable",
        "That name has nothing a code can be made from.",
        mapOf("given" to given),
    )

class GameAlreadyExists(
    gameName: String,
) : GameRefusal(HttpStatus.CONFLICT, "GameAlreadyExists", "That game already exists.", mapOf("gameName" to gameName))

class GameAddressBlank : GameRefusal(HttpStatus.BAD_REQUEST, "GameAddressBlank", "A game needs an address.", emptyMap())

class AddressReserved(
    address: String,
) : GameRefusal(
        HttpStatus.CONFLICT,
        "AddressReserved",
        "That address belongs to the esports listing.",
        mapOf("address" to address),
    )

class AddressTaken(
    gameName: String,
    address: String,
) : GameRefusal(
        HttpStatus.CONFLICT,
        "AddressTaken",
        "That address is already used by another game.",
        mapOf("gameName" to gameName, "address" to address),
    )

class GameNotArchived(
    gameName: String,
) : GameRefusal(
        HttpStatus.CONFLICT,
        "GameNotArchived",
        "A game is archived before it is removed.",
        mapOf("gameName" to gameName),
    )

class GameArchived(
    gameName: String,
) : GameRefusal(
        HttpStatus.CONFLICT,
        "GameArchived",
        "An archived game cannot be newly picked.",
        mapOf("gameName" to gameName),
    )
