package net.blueshell.api.system.frontend

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class DocumentsPageSystemTest : FrontendSystemTestBase() {

    @Test
    fun `documents page triggers statute downloads in both languages`() {
        withPage { page ->
            page.navigate("$frontendUrl/documents")
            page.waitForURL("**/documents")

            waitFor(
                onTimeoutMessage = { "Expected /documents page to render document table" }
            ) {
                page.locator("body").innerText().contains("Statutes")
            }

            val dutchDownload = page.waitForDownload {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Dutch").setExact(true)
                ).first().click()
            }
            assertThat(dutchDownload.suggestedFilename()).isEqualTo("ESA Blueshell - Statuten.pdf")

            val englishDownload = page.waitForDownload {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("English").setExact(true)
                ).first().click()
            }
            assertThat(englishDownload.suggestedFilename()).isEqualTo("ESA Blueshell - Statutes.pdf")
        }
    }
}
