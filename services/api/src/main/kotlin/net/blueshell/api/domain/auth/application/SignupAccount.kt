package net.blueshell.api.domain.auth.application

import net.blueshell.api.domain.user.persistence.User

// The id is non-null by construction, so callers do not re-assert a guarantee the
// resolver already made.
data class SignupAccount(
    val id: Long,
    val user: User,
)
