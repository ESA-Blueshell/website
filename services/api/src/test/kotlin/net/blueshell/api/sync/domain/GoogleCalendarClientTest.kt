package net.blueshell.api.sync.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.cloud.context.environment.EnvironmentChangeEvent
import org.springframework.mock.env.MockEnvironment
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.Base64

class GoogleCalendarClientTest {
    private val environment = MockEnvironment()

    private fun client(serviceAccountJson: String) = GoogleCalendarClient("calendar", serviceAccountJson, environment)

    private fun rotate(
        client: GoogleCalendarClient,
        json: String,
    ) {
        environment.setProperty("google.calendar.serviceAccountJson", json)
        client.onChange(EnvironmentChangeEvent(setOf("google.calendar.serviceAccountJson")))
    }

    // A service account key Google's parser accepts, made up here rather than checked in.
    private fun serviceAccount(email: String): String {
        val key =
            KeyPairGenerator
                .getInstance("RSA")
                .apply { initialize(2048) }
                .generateKeyPair()
                .private
        val body = Base64.getEncoder().encodeToString(key.encoded).chunked(64)
        val pem = "-----BEGIN PRIVATE KEY-----\\n" + body.joinToString("\\n") + "\\n-----END PRIVATE KEY-----\\n"
        return """{"type":"service_account","project_id":"test","private_key_id":"1","private_key":"$pem",""" +
            """"client_email":"$email","client_id":"1","token_uri":"https://oauth2.googleapis.com/token"}"""
    }

    @Test
    fun `a service account that does not build leaves sync off, and a calendar write names the Vault key`() {
        val client = client("{not json")

        assertThat(client.account).isNull()
        assertThatThrownBy { client.addEvent("Game night", null, null, Instant.EPOCH, Instant.EPOCH.plusSeconds(3600)) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("google.calendar.serviceAccountJson")
    }

    @Test
    fun `a service account rotated in Vault is the one calendar calls sign in as next`() {
        val client = client(serviceAccount("first@test.iam.gserviceaccount.com"))

        rotate(client, serviceAccount("second@test.iam.gserviceaccount.com"))

        assertThat(client.account).isEqualTo("second@test.iam.gserviceaccount.com")
    }

    @Test
    fun `a working service account seeded over a broken one turns sync on`() {
        val client = client("{not json")

        rotate(client, serviceAccount("fixed@test.iam.gserviceaccount.com"))

        assertThat(client.account).isEqualTo("fixed@test.iam.gserviceaccount.com")
    }

    @Test
    fun `a rotated service account that is blank or does not parse keeps the one in use`() {
        val client = client(serviceAccount("first@test.iam.gserviceaccount.com"))

        rotate(client, "{not json")
        rotate(client, "")

        assertThat(client.account).isEqualTo("first@test.iam.gserviceaccount.com")
    }

    @Test
    fun `a change to another key leaves the client alone`() {
        val client = client(serviceAccount("first@test.iam.gserviceaccount.com"))
        environment.setProperty("google.calendar.serviceAccountJson", serviceAccount("second@test.iam.gserviceaccount.com"))

        client.onChange(EnvironmentChangeEvent(setOf("brevo.apiKey")))

        assertThat(client.account).isEqualTo("first@test.iam.gserviceaccount.com")
    }
}
