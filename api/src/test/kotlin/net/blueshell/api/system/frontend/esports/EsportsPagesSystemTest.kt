package net.blueshell.api.system.frontend.esports

import net.blueshell.api.system.frontend.FrontendSystemTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class EsportsPagesSystemTest : FrontendSystemTestBase() {

    @Test
    fun `esports landing page exposes game links`() {
        val listedRoutes = listOf(
            "/esports/league-of-legends",
            "/esports/counter-strike-2",
            "/esports/valorant",
            "/esports/rocketleague",
            "/esports/geoguessr"
        )

        withPage { page ->
            page.navigate("$frontendUrl/esports/competitive-scene")
            page.waitForURL("**/esports/competitive-scene")

            waitFor(
                onTimeoutMessage = { "Expected esports landing page intro to render" }
            ) {
                page.locator("body").innerText().contains("Blueshell's Competitive Scene")
            }

            listedRoutes.forEach { route ->
                assertThat(page.locator("a[href='$route']").count())
                    .withFailMessage("Expected esports landing page to contain link '%s'", route)
                    .isGreaterThan(0)
            }
        }
    }

    @Test
    fun `all esports game pages render roster content`() {
        val expectations = listOf(
            GameRouteExpectation(
                path = "/esports/league-of-legends",
                heading = "League of Legends",
                marker = "BS Roestige Ridders"
            ),
            GameRouteExpectation(
                path = "/esports/counter-strike-2",
                heading = "Counter-Strike 2",
                marker = "BS HyperS"
            ),
            GameRouteExpectation(
                path = "/esports/valorant",
                heading = "Valorant",
                marker = "BS Waterboarders"
            ),
            GameRouteExpectation(
                path = "/esports/rocketleague",
                heading = "Rocket League",
                marker = "BS Squirtles"
            ),
            GameRouteExpectation(
                path = "/esports/geoguessr",
                heading = "Geoguessr",
                marker = "Job de Ruijter"
            ),
            GameRouteExpectation(
                path = "/esports/trackmania",
                heading = "Trackmania",
                marker = "[ESABS]"
            )
        )

        withPage { page ->
            expectations.forEach { expectation ->
                page.navigate("$frontendUrl${expectation.path}")
                page.waitForURL("**${expectation.path}")

                waitFor(
                    onTimeoutMessage = {
                        "Expected ${expectation.path} to render heading '${expectation.heading}' and marker '${expectation.marker}'"
                    }
                ) {
                    val body = page.locator("body").innerText()
                    body.contains(expectation.heading) && body.contains(expectation.marker)
                }
            }
        }
    }

    private data class GameRouteExpectation(
        val path: String,
        val heading: String,
        val marker: String
    )
}
