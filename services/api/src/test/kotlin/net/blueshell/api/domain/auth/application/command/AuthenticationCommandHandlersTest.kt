package net.blueshell.api.domain.auth.application.command

import net.blueshell.api.domain.auth.application.AuthenticationService
import net.blueshell.api.domain.auth.command.AuthenticateCommand
import net.blueshell.api.domain.auth.domain.model.AuthenticationSession
import net.blueshell.api.shared.enums.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AuthenticationCommandHandlersTest {

    private val authenticationService = mock<AuthenticationService>()

    @Nested
    inner class Authenticate {

        private val handler = AuthenticateHandler(authenticationService)

        @Test
        fun `authenticates using provided credentials`() {
            val expected = AuthenticationSession(
                token = "jwt-token",
                userId = 5L,
                username = "john",
                expiresAtEpochMs = System.currentTimeMillis() + 60_000,
                roles = setOf(Role.MEMBER),
                addressId = 10L
            )
            whenever(authenticationService.authenticate("john", "Passw0rd!")).thenReturn(expected)

            val result = handler.handle(AuthenticateCommand("john", "Passw0rd!"))

            assertThat(result).isSameAs(expected)
            verify(authenticationService).authenticate("john", "Passw0rd!")
        }
    }
}
