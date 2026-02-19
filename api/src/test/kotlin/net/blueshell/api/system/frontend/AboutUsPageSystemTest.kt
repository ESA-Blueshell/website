package net.blueshell.api.system.frontend

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class AboutUsPageSystemTest : FrontendSystemTestBase() {

    @Test
    fun `about us page renders association history and computed board year`() {
        withPage { page ->
            page.navigate("$frontendUrl/aboutus")
            page.waitForURL("**/aboutus")

            waitFor(
                onTimeoutMessage = { "Expected /aboutus to render core association content" }
            ) {
                page.locator("body").innerText().contains("About us")
            }

            val body = page.locator("body").innerText().replace('\u00A0', ' ')
            assertThat(body).contains("Since its foundation in 1961")
            assertThat(body).contains("year of Blueshell's existence")
            assertThat(body.lowercase()).containsPattern(
                "(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|eleventh|twelfth|\\d+(st|nd|rd|th)) year of blueshell's existence"
            )
        }
    }
}
