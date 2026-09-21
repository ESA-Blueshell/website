package net.blueshell.api.event.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.persistence.User

/**
 * What a roster says about whoever holds a sign-up: two splits at once, an account or a guest, and
 * an account with a membership or without one.
 */
@Schema(name = "EventSignUpKind", enumAsRef = true)
enum class EventSignUpKind {
    GUEST,

    NON_MEMBER,

    MEMBER,
}

/**
 * Membership is asked of the inherited role, not of the stored set: a board member holds MEMBER
 * through the chain without ever carrying the role itself.
 */
fun signUpKindOf(user: User?): EventSignUpKind =
    when {
        user == null -> EventSignUpKind.GUEST
        user.hasAuthority(Role.MEMBER) -> EventSignUpKind.MEMBER
        else -> EventSignUpKind.NON_MEMBER
    }
