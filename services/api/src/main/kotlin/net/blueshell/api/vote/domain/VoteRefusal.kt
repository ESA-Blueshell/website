package net.blueshell.api.vote.domain

import net.blueshell.api.shared.refusal.Refusal
import net.blueshell.api.vote.persistence.VoteKind
import org.springframework.http.HttpStatus

// A code and the facts, never the sentence: `vote/refusals.ts` writes that. See ADR-026.
sealed class VoteRefusal(
    status: HttpStatus,
    code: String,
    summary: String,
    facts: Map<String, Any> = emptyMap(),
) : Refusal(status, code, summary, facts)

class NoAnnualVote(
    kind: VoteKind,
) : VoteRefusal(HttpStatus.NOT_FOUND, "NoAnnualVote", "There is no vote of that kind yet.", mapOf("kind" to kind.name))

class AnnualVoteNotFound(
    kind: VoteKind,
    year: Int,
) : VoteRefusal(HttpStatus.NOT_FOUND, "AnnualVoteNotFound", "There is no such vote.", mapOf("kind" to kind.name, "year" to year))

class AnnualVoteExists(
    kind: VoteKind,
    year: Int,
) : VoteRefusal(HttpStatus.CONFLICT, "AnnualVoteExists", "That year already has that vote.", mapOf("kind" to kind.name, "year" to year))

/** The dates must run opens, then closes, then the reveal no earlier than the close. */
class VoteDatesOutOfOrder :
    VoteRefusal(HttpStatus.BAD_REQUEST, "VoteDatesOutOfOrder", "A vote opens before it closes, and is revealed no earlier than it closes.")

/** The vote is not in a state that allows this; [state] is where it stands. */
class WrongVoteState(
    state: VoteState,
) : VoteRefusal(HttpStatus.CONFLICT, "WrongVoteState", "The vote is not in a state that allows this.", mapOf("state" to state.name))

class BallotSize(
    picks: Int,
    max: Int,
) : VoteRefusal(HttpStatus.BAD_REQUEST, "BallotSize", "A ballot holds one to $max picks.", mapOf("picks" to picks, "max" to max))

/** Two picks name one game, which a ballot may name only once. */
class GamePickedTwice(
    gameId: Long,
) : VoteRefusal(HttpStatus.BAD_REQUEST, "GamePickedTwice", "A ballot names a game once.", mapOf("gameId" to gameId))

/** A pick in a vote whose kind picks tracks names none, or one of another game. */
class PickNeedsTrack(
    gameId: Long,
) : VoteRefusal(HttpStatus.BAD_REQUEST, "PickNeedsTrack", "Each pick names one of its game's tracks.", mapOf("gameId" to gameId))

/** A pick in a vote whose kind picks games alone names a track. */
class PickTakesNoTrack(
    gameId: Long,
) : VoteRefusal(HttpStatus.BAD_REQUEST, "PickTakesNoTrack", "This vote picks games, not tracks.", mapOf("gameId" to gameId))

/** The game was not first released in the vote's year, which this kind requires. */
class GameNotEligible(
    gameId: Long,
    year: Int,
) : VoteRefusal(
        HttpStatus.BAD_REQUEST,
        "GameNotEligible",
        "That game was not released that year.",
        mapOf("gameId" to gameId, "year" to year),
    )

class VoteGameNotFound(
    id: Long,
) : VoteRefusal(HttpStatus.NOT_FOUND, "VoteGameNotFound", "That game does not exist.", mapOf("id" to id))

class TrackNotFound(
    id: Long,
) : VoteRefusal(HttpStatus.NOT_FOUND, "TrackNotFound", "That track does not exist.", mapOf("id" to id))

/** The link is not a YouTube video or a Spotify track. */
class UnsupportedTrackLink :
    VoteRefusal(HttpStatus.BAD_REQUEST, "UnsupportedTrackLink", "That link is not a YouTube video or a Spotify track.")

/** YouTube or Spotify did not describe the link: it is private, removed, or the service is down. */
class TrackLinkUnreadable : VoteRefusal(HttpStatus.BAD_REQUEST, "TrackLinkUnreadable", "That link could not be read.")

/** The link was already picked for another game, and a track belongs to one game. */
class TrackOfAnotherGame(
    trackId: Long,
    gameName: String,
) : VoteRefusal(
        HttpStatus.CONFLICT,
        "TrackOfAnotherGame",
        "That link is already a track of another game.",
        mapOf("trackId" to trackId, "game" to gameName),
    )

/** A track merges only into another live track of the same game. */
class TrackMergeInvalid(
    trackId: Long,
    intoId: Long,
) : VoteRefusal(
        HttpStatus.BAD_REQUEST,
        "TrackMergeInvalid",
        "A track merges only into another track of the same game.",
        mapOf("trackId" to trackId, "intoId" to intoId),
    )

class GameSearchUnavailable : VoteRefusal(HttpStatus.SERVICE_UNAVAILABLE, "GameSearchUnavailable", "Game search is unavailable.")

class CoverNotFound : VoteRefusal(HttpStatus.NOT_FOUND, "CoverNotFound", "That cover could not be fetched.")
