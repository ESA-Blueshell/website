package net.blueshell.api.system.frontend.partners

import net.blueshell.api.system.frontend.FrontendSystemTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class PartnersPagesSystemTest : FrontendSystemTestBase() {

    @Test
    fun `partners landing page shows external affairs contact`() {
        withPage { page ->
            page.navigate("$frontendUrl/partners/become-a-partner")
            page.waitForURL("**/partners/become-a-partner")

            waitFor(
                onTimeoutMessage = { "Expected partner overview page to render contact details" }
            ) {
                page.locator("body").innerText().contains("external-affairs@blueshell.utwente.nl")
            }

            assertThat(page.locator("a[href='mailto:external-affairs@blueshell.utwente.nl']").count()).isGreaterThan(0)
        }
    }

    @Test
    fun `el nino partner page renders location and vacancy links`() {
        withPage { page ->
            page.navigate("$frontendUrl/partners/el-nino")
            page.waitForURL("**/partners/el-nino")

            waitFor(
                onTimeoutMessage = { "Expected El Nino partner page content to render" }
            ) {
                val body = page.locator("body").innerText()
                body.contains("Kuipersdijk 6C, Enschede") &&
                    body.contains("www.elnino.tech/getajob")
            }

            assertThat(page.locator("a[href='https://www.elnino.tech/getajob']").count()).isGreaterThan(0)
            assertThat(page.locator("a[href='https://wa.me/31626978392']").count()).isGreaterThan(0)
        }
    }

    @Test
    fun `marketing maatwerk partner page renders service pillars and normalized phone link`() {
        withPage { page ->
            page.navigate("$frontendUrl/partners/marketing-maatwerk")
            page.waitForURL("**/partners/marketing-maatwerk")

            waitFor(
                onTimeoutMessage = { "Expected Marketing Maatwerk page to render service pillars" }
            ) {
                val body = page.locator("body").innerText()
                body.contains("Professional Websites") &&
                    body.contains("Search Engine Optimization (SEO)") &&
                    body.contains("Reliable Web Hosting")
            }

            val telHref = page.locator("a[href^='tel:']").first().getAttribute("href")
            assertThat(telHref).isEqualTo("tel:+31634218964")
            assertThat(page.locator("a[href='https://marketingmaatwerk.nl/contact/']").count()).isGreaterThan(0)
        }
    }
}
