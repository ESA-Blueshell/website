package net.blueshell.api.esports.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class SeasonNotFoundException(id: Long) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Season with id $id not found")

class TeamNotFoundException(id: Long) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Team with id $id not found")

class RosterEntryNotFoundException(id: Long) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Roster entry with id $id not found")

/**
 * Two seasons cannot run at once: a roster belongs to the season it played in, and overlapping
 * seasons make "which one" unanswerable. Named rather than numbered, so the objection can be
 * read on the form that caused it.
 */
class SeasonOverlapException(name: String) :
    ResponseStatusException(HttpStatus.BAD_REQUEST, "Those dates overlap $name")

class SeasonDatesReversedException :
    ResponseStatusException(HttpStatus.BAD_REQUEST, "A season cannot end before it starts")

class BannerNotFoundException(id: Long) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Banner with id $id not found")

/**
 * A banner narrowed to a team has to be a banner for that team's own game, or it would be
 * unreachable: nothing ever resolves a banner for a game the team does not play.
 */
class BannerTeamPlaysAnotherGameException(team: String, game: String) :
    ResponseStatusException(HttpStatus.BAD_REQUEST, "$team does not play ${game}")
