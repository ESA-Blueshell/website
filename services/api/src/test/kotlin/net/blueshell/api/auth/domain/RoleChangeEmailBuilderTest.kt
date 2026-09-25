package net.blueshell.api.auth.domain

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.persistence.RoleChange
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class RoleChangeEmailBuilderTest {
    private fun change(
        before: Set<Role>,
        after: Set<Role>,
        twoFactor: Boolean,
    ): RoleChange {
        val subject =
            User(
                username = "alice",
                email = "alice@example.com",
                password = "h",
                initials = "A",
                firstName = "Alice",
                lastName = "Doe",
                roles = after.toMutableSet(),
            ).also { it.twoFactorSince = if (twoFactor) Instant.EPOCH else null }
        return RoleChange(subject = subject, actor = subject, rolesBefore = before, rolesAfter = after, changedAt = Instant.EPOCH)
    }

    @Test
    fun `a role waiting on two-factor says to sign in again, where the set-up starts`() {
        val email = createRoleChangeEmail(change(setOf(Role.MEMBER), setOf(Role.MEMBER, Role.BOARD), twoFactor = false), "https://site")

        assertThat(email.markdownContent).contains("You now hold board access")
        assertThat(email.markdownContent).contains("Every sign-in you had has ended")
        assertThat(email.markdownContent).contains("[https://site/login](https://site/login)")
    }

    @Test
    fun `a role that opens at once says nothing of signing in again`() {
        val email = createRoleChangeEmail(change(setOf(Role.MEMBER), setOf(Role.MEMBER, Role.ADMIN), twoFactor = true), "https://site")

        assertThat(email.markdownContent).contains("You now hold administrator access")
        assertThat(email.markdownContent).doesNotContain("sign in again")
    }

    @Test
    fun `a treasurer grant is only told when it waits on two-factor`() {
        val email = createRoleChangeEmail(change(setOf(Role.MEMBER), setOf(Role.MEMBER, Role.TREASURER), twoFactor = false), "https://site")

        assertThat(email.subject).isEqualTo("Your Blueshell access has been extended")
        assertThat(email.markdownContent).contains("You now hold treasurer access")
        assertThat(email.markdownContent).contains("sign in again")
    }
}
