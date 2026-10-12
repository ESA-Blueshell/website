package net.blueshell.api.oidc.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Pins the redirect URIs each downstream client may be sent back to. The
 * authorization server refuses any other, so a URI dropped here is a login
 * that stops working with an error naming no client.
 */
class RegisteredClientsTest {
    private val clients = RegisteredClients().registeredClientRepository("secret")

    @Test
    fun `vault accepts the UI callback and the CLI one`() {
        val vault = clients.findByClientId("vault")

        assertThat(vault).isNotNull
        assertThat(vault!!.redirectUris).containsExactlyInAnyOrder(
            "https://vault.esa-blueshell.nl/ui/vault/auth/oidc/oidc/callback",
            // `vault login -method=oidc` binds a listener on this port and the
            // code must return there; the UI callback ends in the browser.
            "http://localhost:8250/oidc/callback",
        )
    }

    @Test
    fun `headlamp accepts only its own callback`() {
        val headlamp = clients.findByClientId("headlamp")

        assertThat(headlamp).isNotNull
        assertThat(headlamp!!.redirectUris)
            .containsExactly("https://headlamp.esa-blueshell.nl/oidc-callback")
    }
}
