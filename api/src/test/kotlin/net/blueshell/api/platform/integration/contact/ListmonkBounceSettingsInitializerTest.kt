package net.blueshell.api.platform.integration.contact

import net.blueshell.api.platform.config.ListmonkProperties
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

/**
 * Unit tests for [ListmonkBounceSettingsInitializer].
 *
 * Uses [MockRestServiceServer] to intercept HTTP calls and verify that the initializer
 * correctly enables bounce processing and optionally configures a bounce mailbox.
 * No Spring application context is required.
 */
class ListmonkBounceSettingsInitializerTest {

    private val baseUrl = "http://test-listmonk"

    private lateinit var server: MockRestServiceServer
    private lateinit var initializer: ListmonkBounceSettingsInitializer

    @BeforeEach
    fun setUp() {
        setUp(defaultProps())
    }

    private fun setUp(props: ListmonkProperties) {
        val builder = RestClient.builder().baseUrl(baseUrl)
        server = MockRestServiceServer.bindTo(builder).build()
        val restClient = builder.build()
        initializer = ListmonkBounceSettingsInitializer(props, restClient)
    }

    private fun defaultProps(mailboxEnabled: Boolean = false): ListmonkProperties =
        ListmonkProperties(
            api = ListmonkProperties.ApiProperties(
                baseUrl = "$baseUrl/api",
                username = "listmonk",
                password = "listmonk",
                tokenFile = "/nonexistent/path",
            ),
            bounce = ListmonkProperties.BounceProperties(
                mailbox = ListmonkProperties.BounceProperties.MailboxProperties(
                    enabled = mailboxEnabled,
                    host = if (mailboxEnabled) "imap.example.com" else "",
                    port = 993,
                    username = if (mailboxEnabled) "bounce@example.com" else "",
                    password = if (mailboxEnabled) "secret" else "",
                )
            )
        )

    private fun settingsResponse(bounceEnabled: Boolean, withMailboxes: Boolean = false): String {
        val mailboxPart = if (withMailboxes) """, "bounce.mailboxes": [{"host":"existing"}]""" else """, "bounce.mailboxes": []"""
        return """{"data": {"bounce.enabled": $bounceEnabled, "app.name": "Listmonk"$mailboxPart}}"""
    }

    @Nested
    inner class BounceEnabling {

        @Test
        fun `enables bounce processing when it is disabled`() {
            server.expect(requestTo("$baseUrl/api/settings"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(settingsResponse(bounceEnabled = false), MediaType.APPLICATION_JSON))
            server.expect(requestTo("$baseUrl/api/settings"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"bounce.enabled\":true")))
                .andRespond(withNoContent())

            initializer.configureBounceSettings()

            server.verify()
        }

        @Test
        fun `skips PUT when bounce is already enabled and no mailbox is configured`() {
            server.expect(requestTo("$baseUrl/api/settings"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(settingsResponse(bounceEnabled = true), MediaType.APPLICATION_JSON))
            // No PUT expected

            initializer.configureBounceSettings()

            server.verify()
        }
    }

    @Nested
    inner class MailboxConfiguration {

        @BeforeEach
        fun setUpWithMailbox() {
            setUp(defaultProps(mailboxEnabled = true))
        }

        @Test
        fun `configures IMAP mailbox when bounce mailbox is enabled`() {
            server.expect(requestTo("$baseUrl/api/settings"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(settingsResponse(bounceEnabled = false), MediaType.APPLICATION_JSON))
            server.expect(requestTo("$baseUrl/api/settings"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                    org.hamcrest.Matchers.containsString("\"bounce.enabled\":true"),
                    org.hamcrest.Matchers.containsString("imap.example.com"),
                    org.hamcrest.Matchers.containsString("bounce@example.com"),
                )))
                .andRespond(withNoContent())

            initializer.configureBounceSettings()

            server.verify()
        }

        @Test
        fun `does not overwrite existing bounce mailboxes`() {
            server.expect(requestTo("$baseUrl/api/settings"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(settingsResponse(bounceEnabled = false, withMailboxes = true), MediaType.APPLICATION_JSON))
            server.expect(requestTo("$baseUrl/api/settings"))
                .andExpect(method(HttpMethod.PUT))
                // The existing mailbox should still be present, the new one should NOT be added
                .andExpect(content().string(org.hamcrest.Matchers.not(
                    org.hamcrest.Matchers.containsString("imap.example.com")
                )))
                .andRespond(withNoContent())

            initializer.configureBounceSettings()

            server.verify()
        }

        @Test
        fun `skips mailbox config but still enables bounce when host is blank`() {
            val propsWithBlankHost = ListmonkProperties(
                api = ListmonkProperties.ApiProperties(baseUrl = "$baseUrl/api", tokenFile = "/nonexistent"),
                bounce = ListmonkProperties.BounceProperties(
                    mailbox = ListmonkProperties.BounceProperties.MailboxProperties(enabled = true, host = "", username = "")
                )
            )
            setUp(propsWithBlankHost)

            server.expect(requestTo("$baseUrl/api/settings"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(settingsResponse(bounceEnabled = false), MediaType.APPLICATION_JSON))
            server.expect(requestTo("$baseUrl/api/settings"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"bounce.enabled\":true")))
                .andRespond(withNoContent())

            initializer.configureBounceSettings()

            server.verify()
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `does not crash when Listmonk returns an error response`() {
            server.expect(requestTo("$baseUrl/api/settings"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
            // data is null → skips, no PUT, no exception

            initializer.configureBounceSettings()

            server.verify()
        }

        @Test
        fun `does not crash when settings data is missing`() {
            server.expect(requestTo("$baseUrl/api/settings"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"data\": null}", MediaType.APPLICATION_JSON))

            initializer.configureBounceSettings()

            server.verify()
        }
    }
}
