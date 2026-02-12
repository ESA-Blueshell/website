package net.blueshell.api.domain.auth.application.exception

class InvalidRecoveryTokenException(message: String = "Invalid or expired recovery token") : RuntimeException(message)
