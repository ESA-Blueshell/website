package net.blueshell.api.domain.esports.application.exception

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class SeasonNotFoundException(id: Long) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Season with id $id not found")

class TeamNotFoundException(id: Long) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Team with id $id not found")

class RosterEntryNotFoundException(id: Long) :
    ResponseStatusException(HttpStatus.NOT_FOUND, "Roster entry with id $id not found")
