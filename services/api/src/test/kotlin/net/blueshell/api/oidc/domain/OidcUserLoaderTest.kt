package net.blueshell.api.oidc.domain

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.api.UserNotFoundException
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OidcUserLoaderTest {
    private val users = mock<UserService>()
    private val loader = OidcUserLoader(users)

    @Test
    fun `a dormant role reaches no tool through a token`() {
        val admin =
            User(
                username = "root",
                email = "r@example.com",
                password = "h",
                initials = "R",
                firstName = "Ro",
                lastName = "Ot",
                roles = mutableSetOf(Role.ADMIN),
            ).also { it.id = 1 }
        whenever(users.findByUsername("root")).thenReturn(admin)

        assertThat(loader.load("root")?.roles).doesNotContain(Role.ADMIN)
    }

    @Test
    fun `nobody by that name loads as nothing`() {
        whenever(users.findByUsername("nobody")).thenThrow(UserNotFoundException("nobody"))

        assertThat(loader.load("nobody")).isNull()
    }
}
