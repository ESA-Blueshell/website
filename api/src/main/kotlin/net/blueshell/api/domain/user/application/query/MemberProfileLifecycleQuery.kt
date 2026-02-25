package net.blueshell.api.domain.user.application.query

/**
 * Query object for filtering member-profile lifecycle rows through specifications.
 */
data class MemberProfileLifecycleQuery(
    var userId: Long? = null,
    var userIds: Set<Long>? = null,
    var softDeleted: Boolean? = null
)
