package net.blueshell.api.system.frontend.events

import net.blueshell.api.system.frontend.FrontendSystemTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class CircuitShowdownPageSystemTest : FrontendSystemTestBase() {

    @Test
    fun `circuit showdown page renders group standings and playoffs`() {
        withPage { page ->
            page.navigate("$frontendUrl/events/circuitShowdown")
            page.waitForURL("**/events/circuitShowdown")

            waitFor(
                onTimeoutMessage = { "Expected circuit showdown page heading to render" }
            ) {
                page.locator("body").innerText().contains("What is the Blueshell Circuit Showdown?")
            }

            val body = page.locator("body").innerText()
            assertThat(body).contains("Group A")
            assertThat(body).contains("NyperS")
            assertThat(body).contains("Group B")
            assertThat(body).contains("Waterboarders")
            assertThat(body).contains("Playoffs")
            assertThat(body).contains("Hatsune Miku Fanclub")
        }
    }
}
