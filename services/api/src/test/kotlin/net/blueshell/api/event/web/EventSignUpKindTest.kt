package net.blueshell.api.event.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EventSignUpKindTest {
    private fun userWith(vararg roles: Role): User =
        User(
            username = "someone",
            email = "someone@example.com",
            password = "hashed",
            initials = "S.",
            firstName = "Some",
            lastName = "One",
            roles = roles.toMutableSet(),
        )

    @Test
    fun `a sign-up without an account is a guest`() {
        assertThat(signUpKindOf(null)).isEqualTo(EventSignUpKind.GUEST)
    }

    @Test
    fun `an account with an active membership is a member`() {
        assertThat(signUpKindOf(userWith(Role.MEMBER))).isEqualTo(EventSignUpKind.MEMBER)
    }

    @Test
    fun `an account without a membership is a non-member`() {
        assertThat(signUpKindOf(userWith(Role.GUEST))).isEqualTo(EventSignUpKind.NON_MEMBER)
    }

    @Test
    fun `a role that inherits member counts as a member without carrying the role itself`() {
        assertThat(signUpKindOf(userWith(Role.BOARD))).isEqualTo(EventSignUpKind.MEMBER)
        assertThat(signUpKindOf(userWith(Role.ADMIN))).isEqualTo(EventSignUpKind.MEMBER)
    }

    @Test
    fun `a committee seat carries membership through the same chain`() {
        assertThat(signUpKindOf(userWith(Role.COMMITTEE))).isEqualTo(EventSignUpKind.MEMBER)
    }
}
