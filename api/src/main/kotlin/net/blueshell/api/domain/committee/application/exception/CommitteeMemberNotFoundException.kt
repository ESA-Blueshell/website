package net.blueshell.api.domain.committee.application.exception

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class CommitteeMemberNotFoundException(committeeId: Long, userId: Long) :
    ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Committee member not found: committeeId=$committeeId, userId=$userId"
    )
