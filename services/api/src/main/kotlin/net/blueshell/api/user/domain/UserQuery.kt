package net.blueshell.api.user.domain

/**
 * Query object for filtering users in dynamic searches.
 * Used in the application layer for building JPA Specifications.
 */
data class UserQuery(
    var isMember: Boolean? = null,
    var username: String? = null,
    var enabled: Boolean? = null
)
