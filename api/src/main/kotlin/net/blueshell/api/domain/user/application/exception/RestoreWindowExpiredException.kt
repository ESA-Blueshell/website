package net.blueshell.api.domain.user.application.exception

class RestoreWindowExpiredException(userId: Long) :
    RuntimeException("Restore window has expired for user id: $userId")
