package net.blueshell.api.oidc.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.cloud.context.environment.EnvironmentChangeEvent
import org.springframework.mock.env.MockEnvironment

class VaultClientSecretRotationTest {
    private val clients = RegisteredClients().registeredClientRepository("first")
    private val environment = MockEnvironment()
    private val rotation = VaultClientSecretRotation(clients, environment)

    private fun vaultHolds(secret: String) {
        environment.setProperty("auth.clients.vault.secret", secret)
        rotation.onChange(EnvironmentChangeEvent(setOf("auth.clients.vault.secret")))
    }

    @Test
    fun `a rotated secret replaces the client's, under the same id`() {
        val id = clients.findByClientId("vault")!!.id

        vaultHolds("second")

        val vault = clients.findByClientId("vault")!!
        assertThat(vault.clientSecret).isEqualTo("{noop}second")
        assertThat(vault.id).isEqualTo(id)
    }

    @Test
    fun `a blank secret is refused and the one in use stays`() {
        vaultHolds("")

        assertThat(clients.findByClientId("vault")!!.clientSecret).isEqualTo("{noop}first")
    }

    @Test
    fun `a change to another key leaves the client alone`() {
        environment.setProperty("auth.clients.vault.secret", "second")
        rotation.onChange(EnvironmentChangeEvent(setOf("brevo.apiKey")))

        assertThat(clients.findByClientId("vault")!!.clientSecret).isEqualTo("{noop}first")
    }
}
