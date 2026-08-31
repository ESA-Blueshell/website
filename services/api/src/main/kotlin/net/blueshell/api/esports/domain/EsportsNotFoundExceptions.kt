package net.blueshell.api.esports.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

// Sentences rather than codes: no dialog can provoke a missing row, so a code would reach no branch.
class SeasonNotFoundException(id: Long) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Season with id $id not found")

class TeamNotFoundException(id: Long) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Team with id $id not found")

class RosterEntryNotFoundException(id: Long) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Roster entry with id $id not found")
