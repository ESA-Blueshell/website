package net.blueshell.api.auth.domain

import net.blueshell.api.user.persistence.User

// The id is non-null by construction, so callers do not re-assert a guarantee the
// resolver already made.
data class SignupAccount(
    val id: Long,
    val user: User,
)
