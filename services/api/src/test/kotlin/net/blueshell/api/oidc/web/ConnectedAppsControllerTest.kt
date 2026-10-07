package net.blueshell.api.oidc.web

import net.blueshell.api.oidc.domain.ConnectedApp
import net.blueshell.api.oidc.domain.ConnectedApps
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class ConnectedAppsControllerTest {
    private val connectedApps = mock<ConnectedApps>()
    private val controller = ConnectedAppsController(connectedApps)

    private val ada = UserPrincipal(1, "ada", "hash", true, setOf(Role.MEMBER), null, null)
    private val authorizedAt = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `lists the member's connected apps as responses`() {
        whenever(connectedApps.of("ada")).thenReturn(listOf(ConnectedApp("pinger-app", "Pinger", authorizedAt)))

        assertThat(controller.connectedApps(ada))
            .containsExactly(ConnectedAppResponse("pinger-app", "Pinger", authorizedAt))
    }

    @Test
    fun `revokes the named app for the member`() {
        controller.revokeConnectedApp(ada, "pinger-app")

        verify(connectedApps).revoke("ada", "pinger-app")
    }
}
