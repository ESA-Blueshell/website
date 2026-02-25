package net.blueshell.api.domain.user.application.exception

class RestoreConflictException(reason: String) :
    RuntimeException("Cannot restore user: $reason")
