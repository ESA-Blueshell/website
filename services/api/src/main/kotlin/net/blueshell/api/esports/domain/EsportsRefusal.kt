package net.blueshell.api.esports.domain

import org.springframework.http.HttpStatus

/**
 * A refusal the esports pages put to somebody, carrying what happened rather than the sentence
 * they read.
 *
 * The api used to write those sentences. Two of them were composed from data — pluralising and
 * joining clauses — and one of those had a second implementation in TypeScript, because the
 * dialog asks the same thing before the act that the api answers after it. One sentence, two
 * languages, neither aware of the other.
 *
 * So a refusal carries a [code] and its [facts], and the frontend says what it means. [summary]
 * is the same sentence for every occurrence of a code: it never interpolates and never
 * pluralises, so it stays useful in a log and to a caller that is not a browser without being
 * the display string the frontend is composing. See ADR-026.
 */
sealed class EsportsRefusal(
    val status: HttpStatus,
    val code: String,
    val summary: String,
    val facts: Map<String, Any>,
) : RuntimeException(summary)

/**
 * A code naming no game.
 *
 * Bad request rather than not-found: it is the same answer a code outside the compiled list used
 * to get, when the framework could not turn it into one.
 */
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

/**
 * A name a code cannot be made from. A code carries no punctuation and no case, so a name that
 * is nothing but punctuation leaves nothing behind.
 */
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

/**
 * A game that carries history cannot go, and the offer to remove it says what it holds.
 *
 * The counts are facts rather than a sentence: how they are pluralised and joined is the
 * frontend's, which composes the same sentence before the act from `GET /games/{game}/contents`.
 */
class GameHoldsHistory(gameName: String, teams: Long, players: Long) : EsportsRefusal(
    HttpStatus.CONFLICT,
    "GameHoldsHistory",
    "That game cannot be removed.",
    mapOf("gameName" to gameName, "teams" to teams, "players" to players),
)

/**
 * A game cannot leave a season while a team is fielded in it there: the teams would be left in a
 * season that does not list the game they played.
 */
class GameFieldedInSeason(gameName: String, teams: Int) : EsportsRefusal(
    HttpStatus.CONFLICT,
    "GameFieldedInSeason",
    "That game cannot be taken out of the season.",
    mapOf("gameName" to gameName, "teams" to teams),
)

class GameAddressBlank : EsportsRefusal(
    HttpStatus.BAD_REQUEST,
    "GameAddressBlank",
    "A game's page needs an address.",
    emptyMap(),
)

/** The esports index answers to an address of its own, which no game may take. */
class AddressReserved(address: String) : EsportsRefusal(
    HttpStatus.CONFLICT,
    "AddressReserved",
    "That address belongs to the esports index.",
    mapOf("address" to address),
)

/** An address is how somebody reaches a page; two games cannot share one. */
class AddressTaken(gameName: String, address: String) : EsportsRefusal(
    HttpStatus.CONFLICT,
    "AddressTaken",
    "That address is already used by another game.",
    mapOf("gameName" to gameName, "address" to address),
)

/**
 * Two seasons cannot run at once: a roster belongs to the season it played in, and overlapping
 * seasons make "which one" unanswerable. The season clashed with is named, so the objection can
 * be read on the form that caused it.
 */
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

/**
 * A save named a picture that is not in storage.
 *
 * Refused rather than ignored: a picture is uploaded on its own and put on a record by the save
 * that names it, and a save that quietly drops the name leaves whoever chose it looking at a
 * dialog that closed and a record that did not change.
 */
class PictureNotStored : EsportsRefusal(
    HttpStatus.BAD_REQUEST,
    "PictureNotStored",
    "That picture is not in storage.",
    emptyMap(),
)
