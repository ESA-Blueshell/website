package net.blueshell.api.user.domain

/**
 * What a caller is narrowing the user listing by.
 *
 * [search] is what somebody types into a picker: the table is larger than any picker can
 * render, so the answer has to be narrowed before it is sent rather than after.
 */
data class UserQuery(
    var isMember: Boolean? = null,
    var username: String? = null,
    var enabled: Boolean? = null,
    var search: String? = null,
)
