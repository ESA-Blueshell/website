package net.blueshell.api.auth.domain

class InvalidRecoveryTokenException(message: String = "Invalid or expired recovery token") : RuntimeException(message)
