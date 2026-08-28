package net.blueshell.api.user.domain

sealed class ErasureException(message: String) : RuntimeException(message) {
    class NotFound(userId: Long) : ErasureException("Deleted user not found for id: $userId")
    class Expired(userId: Long) : ErasureException("Restore window has expired for user id: $userId")
    class Conflict(reason: String) : ErasureException("Cannot restore user: $reason")
}
