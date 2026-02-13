package net.blueshell.api.domain.board.application.exception

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

class DuplicateBoardException(name: String, startDate: LocalDate) :
    ResponseStatusException(
        HttpStatus.CONFLICT,
        "Board '$name' starting on $startDate already exists"
    )
