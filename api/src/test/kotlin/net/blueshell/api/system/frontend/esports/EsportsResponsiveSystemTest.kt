package net.blueshell.api.system.frontend.esports

import net.blueshell.api.system.frontend.FrontendSystemTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class EsportsResponsiveSystemTest : FrontendSystemTestBase() {

    @Test
    fun `valorant page renders team details correctly on mobile layout`() {
        withPage { page ->
            page.setViewportSize(480, 900)
            page.navigate("$frontendUrl/esports/valorant")
            page.waitForURL("**/esports/valorant")

            waitFor(
                onTimeoutMessage = { "Expected mobile Valorant page to render team details labels" }
            ) {
                val body = page.locator("body").innerText()
                body.contains("BS Waterboarders") &&
                    body.contains("Players") &&
                    body.contains("Substitutes") &&
                    body.contains("Coach")
            }

            assertThat(page.locator("text=BS G.G.C. Yaptown").count()).isGreaterThan(0)
            assertThat(page.locator("text=Reinier Algra").count()).isGreaterThan(0)
        }
    }
}
