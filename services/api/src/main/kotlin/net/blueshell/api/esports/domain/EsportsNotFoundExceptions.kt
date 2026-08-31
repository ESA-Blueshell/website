package net.blueshell.api.esports.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * A row somebody named that is not there.
 *
 * These keep their sentences rather than becoming codes like the refusals in [EsportsRefusal]. A
 * dialog cannot provoke one: reaching it takes a hand-built request naming a row that does not
 * exist, so there is no copy for a reader to meet and no frontend branch a code would reach.
 */
class SeasonNotFoundException(id: Long) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Season with id $id not found")

class TeamNotFoundException(id: Long) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Team with id $id not found")

class RosterEntryNotFoundException(id: Long) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Roster entry with id $id not found")
