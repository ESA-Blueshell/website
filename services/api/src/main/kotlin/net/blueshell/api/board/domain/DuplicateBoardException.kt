package net.blueshell.api.board.domain

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * A board's number is its identity, so two of the boards that exist cannot share one.
 *
 * Refused here rather than left to the unique key, so the answer says which number is taken
 * instead of reporting a constraint by name.
 */
class DuplicateBoardException(number: Int) :
    ResponseStatusException(
        HttpStatus.CONFLICT,
        "Board $number already exists"
    )
