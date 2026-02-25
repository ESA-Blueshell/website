package net.blueshell.api.domain.user.application.exception

class DeletedUserNotFoundException(userId: Long) :
    RuntimeException("Deleted user not found for id: $userId")
