package net.blueshell.api.system.frontend

import net.blueshell.api.factory.blog.persistence.BlogFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@Tag("system")
class PublicRoutesSmokeSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var blogFactory: BlogFactory

    @Test
    fun `public routes render expected base content`() {
        blogFactory.create(
            title = "Smoke Blog ${System.currentTimeMillis()}",
            html = "<h1>Smoke blog</h1>"
        )

        val expectations = listOf(
            RouteExpectation("/", "Who are we?"),
            RouteExpectation("/contact", "Contact"),
            RouteExpectation("/committees", "Committees"),
            RouteExpectation("/esports/competitive-scene", "Esports"),
            RouteExpectation("/membership", "Membership"),
            RouteExpectation("/documents", "Documents"),
            RouteExpectation("/aboutus", "Association"),
            RouteExpectation("/board", "Board"),
            RouteExpectation("/esports/league-of-legends", "League of Legends"),
            RouteExpectation("/esports/counter-strike-2", "Counter-Strike 2"),
            RouteExpectation("/esports/valorant", "Valorant"),
            RouteExpectation("/esports/rocketleague", "Rocket League"),
            RouteExpectation("/esports/geoguessr", "Geoguessr"),
            RouteExpectation("/esports/trackmania", "Trackmania"),
            RouteExpectation("/partners/become-a-partner", "Partners"),
            RouteExpectation("/partners/el-nino", "El Niño"),
            RouteExpectation("/partners/marketing-maatwerk", "Marketing Maatwerk"),
            RouteExpectation("/partners/connectworks", "Connectworks"),
            RouteExpectation("/login", "Login"),
            RouteExpectation("/login/forgor", "Forgot Password"),
            RouteExpectation("/account/create", "Create Account"),
            RouteExpectation("/account/reset-password?token=smoke-token", "Reset Password", "/account/reset-password"),
            RouteExpectation("/account/activate/member?token=smoke-token", "Activate Member", "/account/activate/member"),
            RouteExpectation("/account/activate/user?token=smoke-token", "Account Activation", "/account/activate/user"),
            RouteExpectation("/events", "Events"),
            RouteExpectation("/events/calendar", "Events", "/events"),
            RouteExpectation("/events/circuitShowdown", "Circuit Showdown"),
            RouteExpectation("/blogs", "Newsletters")
        )

        withPage { page ->
            page.setViewportSize(1400, 1000)
            expectations.forEach { expectation ->
                page.navigate("$frontendUrl${expectation.path}")
                waitFor(
                    timeoutMs = 12_000,
                    onTimeoutMessage = {
                        "Expected route ${expectation.path} to render text '${expectation.expectedText}' " +
                            "and url containing '${expectation.expectedUrlContains}'"
                    }
                ) {
                    page.url().contains(expectation.expectedUrlContains) &&
                        page.locator("body").innerText().lowercase().contains(expectation.expectedText.lowercase())
                }
            }
        }
    }

    @Test
    fun `unknown route renders not found page`() {
        withPage { page ->
            val missingPath = "/missing-smoke-route-${System.currentTimeMillis()}"
            page.navigate("$frontendUrl$missingPath")

            waitFor(
                onTimeoutMessage = { "Expected not-found content for route $missingPath" }
            ) {
                page.locator("body").innerText().lowercase().contains("uh oh")
            }
            assertThat(page.url()).contains(missingPath)
        }
    }

    private data class RouteExpectation(
        val path: String,
        val expectedText: String,
        val expectedUrlContains: String = path.substringBefore("?")
    )
}
