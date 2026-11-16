package net.blueshell.api.vote.web

import net.blueshell.api.vote.domain.BallotSummary
import net.blueshell.api.vote.domain.CatalogueGame
import net.blueshell.api.vote.domain.GameSummary
import net.blueshell.api.vote.domain.PickSummary
import net.blueshell.api.vote.domain.Placing
import net.blueshell.api.vote.domain.TrackSummary
import net.blueshell.api.vote.domain.VoteSummary

fun CatalogueGame.asResponse() = CatalogueGameResponse(igdbId, name, firstReleaseYear, coverImageId)

fun GameSummary.asResponse() = VoteGameResponse(id, igdbId, name, firstReleaseYear, coverPath)

fun TrackSummary.asResponse() = VoteTrackResponse(id, game.asResponse(), source, externalId, url, title, thumbnailPath)

fun PickSummary.asResponse() = VotePickResponse(game.asResponse(), track?.asResponse())

fun Placing.asResponse() = VotePlacingResponse(place, game.asResponse(), track?.asResponse(), points, voters)

fun VoteSummary.asResponse() =
    AnnualVoteResponse(
        kind = kind,
        year = year,
        state = state,
        opensAt = opensAt,
        closesAt = closesAt,
        revealAt = revealAt,
        ballots = ballots,
        results = results.map { it.asResponse() },
        points = kind.points,
        picksTrack = kind.picksTrack,
        releasedInYear = kind.releasedInYear,
    )

fun BallotSummary?.asResponse() =
    VoteBallotResponse(
        version = this?.version,
        submittedAt = this?.submittedAt,
        picks = this?.picks.orEmpty().map { it.asResponse() },
    )
