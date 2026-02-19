package net.blueshell.api.system.frontend

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class BoardPageSystemTest : FrontendSystemTestBase() {

    @Test
    fun `board page expands and collapses archived boards`() {
        withPage { page ->
            page.navigate("$frontendUrl/board")
            page.waitForURL("**/board")

            waitFor(
                onTimeoutMessage = { "Expected board page to render active board section" }
            ) {
                page.getByText("9th Board", Page.GetByTextOptions().setExact(true)).count() > 0
            }

            val oldBoardMember = page.getByText("Michal Rokita", Page.GetByTextOptions().setExact(true)).first()
            assertThat(page.getByText("Emma Dokter", Page.GetByTextOptions().setExact(true)).first().isVisible()).isTrue()
            assertThat(oldBoardMember.isVisible()).isFalse()

            val eighthBoardToggle = page.locator("[role='button']")
                .filter(Locator.FilterOptions().setHasText("8th Board"))
                .first()

            eighthBoardToggle.click()
            waitFor(
                onTimeoutMessage = { "Expected archived board section to expand after clicking 8th Board" }
            ) {
                oldBoardMember.isVisible()
            }

            eighthBoardToggle.click()
            waitFor(
                onTimeoutMessage = { "Expected archived board section to collapse after clicking 8th Board again" }
            ) {
                !oldBoardMember.isVisible()
            }
        }
    }
}
