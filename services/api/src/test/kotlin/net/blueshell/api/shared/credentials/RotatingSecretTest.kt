package net.blueshell.api.shared.credentials

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment

class RotatingSecretTest {
    private val environment = MockEnvironment().withProperty("brevo.apiKey", "first")
    private val secret = RotatingSecret(environment, "brevo.apiKey")

    @Test
    fun `a rotated value is the one read next`() {
        assertThat(secret.current()).isEqualTo("first")

        environment.setProperty("brevo.apiKey", "second")

        assertThat(secret.current()).isEqualTo("second")
    }

    @Test
    fun `a value gone blank keeps the one in use`() {
        secret.current()

        environment.setProperty("brevo.apiKey", "")

        assertThat(secret.current()).isEqualTo("first")
    }

    @Test
    fun `a value never set reads blank`() {
        assertThat(RotatingSecret(environment, "discord.botToken").current()).isEmpty()
    }
}
