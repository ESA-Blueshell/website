package net.blueshell.api.user.domain

/**
 * Query object for filtering address lifecycle rows through specifications.
 */
data class AddressLifecycleQuery(
    var id: Long? = null,
    var ids: Set<Long>? = null,
    var softDeleted: Boolean? = null
)

/**
 * Query object for filtering member-profile lifecycle rows through specifications.
 */
data class ProfileLifecycleQuery(
    var userId: Long? = null,
    var userIds: Set<Long>? = null,
    var softDeleted: Boolean? = null
)
