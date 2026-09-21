package net.blueshell.api.event.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.persistence.User

/**
 * What a roster reader needs to know about whoever holds a sign-up.
 *
 * Two splits at once: a sign-up belongs to an account or to a guest, and an account holder does or
 * does not hold a membership.
 */
@Schema(name = "EventSignUpKind", enumAsRef = true)
enum class EventSignUpKind {
    /** No account at all — signed up through the guest form, reached by an access link. */
    GUEST,

    /** An account without a membership. */
    NON_MEMBER,

    /** An account with a membership. */
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
