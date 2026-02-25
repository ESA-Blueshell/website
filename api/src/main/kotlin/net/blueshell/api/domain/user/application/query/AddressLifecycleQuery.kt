package net.blueshell.api.domain.user.application.query

/**
 * Query object for filtering address lifecycle rows through specifications.
 */
data class AddressLifecycleQuery(
    var id: Long? = null,
    var ids: Set<Long>? = null,
    var softDeleted: Boolean? = null
)
