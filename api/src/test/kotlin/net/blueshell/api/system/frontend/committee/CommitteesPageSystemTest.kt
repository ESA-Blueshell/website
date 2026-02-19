package net.blueshell.api.system.frontend.committee

import com.microsoft.playwright.Route
import net.blueshell.api.factory.committee.persistence.CommitteeFactory
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@Tag("system")
class CommitteesPageSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var committeeFactory: CommitteeFactory

    @Test
    fun `committees page shows empty state when there are no committees`() {
        withPage { page ->
            page.navigate("$frontendUrl/committees")
            page.waitForURL("**/committees")

            waitFor(
                onTimeoutMessage = { "Expected empty-state message on committees page when no committees are present" }
            ) {
                page.getByText("No committees found.", com.microsoft.playwright.Page.GetByTextOptions().setExact(true))
                    .count() > 0
            }
        }
    }

    @Test
    fun `committees page renders committee cards from api`() {
        val marker = System.currentTimeMillis()
        val committee = committeeFactory.create(
            name = "System Committee $marker",
            description = "Committee description $marker"
        )

        withPage { page ->
            page.navigate("$frontendUrl/committees")
            page.waitForURL("**/committees")

            waitFor(
                onTimeoutMessage = { "Expected committee ${committee.name} to be shown on committees page" }
            ) {
                page.getByText(committee.name, com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).count() > 0
            }
            assertThat(
                page.getByText(committee.description, com.microsoft.playwright.Page.GetByTextOptions().setExact(false))
                    .count()
            ).isGreaterThan(0)
        }
    }

    @Test
    fun `committees page falls back to empty state when api fails`() {
        withPage { page ->
            page.route(
                "**/api/committees",
                { route: Route ->
                    route.fulfill(
                        Route.FulfillOptions()
                            .setStatus(500)
                            .setContentType("application/json")
                            .setBody("""{"message":"boom"}""")
                    )
                }
            )

            page.navigate("$frontendUrl/committees")
            page.waitForURL("**/committees")

            waitFor(
                onTimeoutMessage = { "Expected committees page to render fallback state when committees request fails" }
            ) {
                page.getByText("No committees found.", com.microsoft.playwright.Page.GetByTextOptions().setExact(true))
                    .count() > 0
            }
        }
    }
}
