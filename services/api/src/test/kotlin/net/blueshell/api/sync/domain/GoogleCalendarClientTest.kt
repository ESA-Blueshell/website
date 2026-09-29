package net.blueshell.api.sync.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.cloud.context.environment.EnvironmentChangeEvent
import org.springframework.mock.env.MockEnvironment
import org.springframework.test.util.ReflectionTestUtils
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.Base64

class GoogleCalendarClientTest {
    private val environment = MockEnvironment()

    private fun client(serviceAccountJson: String) =
        GoogleCalendarClient().apply {
            ReflectionTestUtils.setField(this, "calendarId", "calendar")
            ReflectionTestUtils.setField(this, "serviceAccountJson", serviceAccountJson)
            ReflectionTestUtils.setField(this, "environment", environment)
            init()
        }

    private fun configured(client: GoogleCalendarClient) = ReflectionTestUtils.getField(client, "service") != null

    private fun rotate(
        client: GoogleCalendarClient,
        json: String,
    ) {
        environment.setProperty("google.calendar.serviceAccountJson", json)
        client.onChange(EnvironmentChangeEvent(setOf("google.calendar.serviceAccountJson")))
    }

    // A service account key Google's parser accepts, made up here rather than checked in.
    private fun serviceAccount(): String {
        val key =
            KeyPairGenerator
                .getInstance("RSA")
                .apply { initialize(2048) }
                .generateKeyPair()
                .private
        val body = Base64.getEncoder().encodeToString(key.encoded).chunked(64)
        val pem = "-----BEGIN PRIVATE KEY-----\\n" + body.joinToString("\\n") + "\\n-----END PRIVATE KEY-----\\n"
        return """{"type":"service_account","project_id":"test","private_key_id":"1","private_key":"$pem",""" +
            """"client_email":"sync@test.iam.gserviceaccount.com","client_id":"1",""" +
            """"token_uri":"https://oauth2.googleapis.com/token"}"""
    }

    @Test
    fun `without a service account the client starts, and a calendar write names the Vault key to seed`() {
        val client = client("")

        assertThatThrownBy { client.addEvent("Game night", null, null, Instant.EPOCH, Instant.EPOCH.plusSeconds(3600)) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("google.calendar.serviceAccountJson")
    }

    @Test
    fun `a service account seeded in Vault while the api runs turns calendar sync on`() {
        val client = client("")

        rotate(client, serviceAccount())

        assertThat(configured(client)).isTrue()
    }

    @Test
    fun `a rotated service account that does not parse keeps the client in use`() {
        val client = client(serviceAccount())

        rotate(client, "{not json")

        assertThat(configured(client)).isTrue()
    }

    @Test
    fun `a change to another key leaves the client alone`() {
        val client = client("")
        environment.setProperty("google.calendar.serviceAccountJson", serviceAccount())

        client.onChange(EnvironmentChangeEvent(setOf("brevo.apiKey")))

        assertThat(configured(client)).isFalse()
    }
}
