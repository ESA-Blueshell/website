package net.blueshell.api.system.frontend

import net.blueshell.api.factory.committee.persistence.CommitteeFactory
import net.blueshell.api.factory.event.persistence.EventFactory
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.helper.AuthHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@Tag("system")
class ProtectedRoutesSmokeSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var userFactory: UserFactory

    @Autowired
    private lateinit var committeeFactory: CommitteeFactory

    @Autowired
    private lateinit var eventFactory: EventFactory

    @Test
    fun `board can open protected non-admin routes`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val committee = committeeFactory.create(name = "Protected Smoke Committee ${System.currentTimeMillis()}")
        val event = eventFactory.create(
            committee = committee,
            approved = true,
            signUp = true,
            title = "Protected Smoke Event ${System.currentTimeMillis()}"
        )
        val eventId = checkNotNull(event.id) { "Expected event id for protected route smoke test" }

        val expectations = listOf(
            ProtectedRouteExpectation("/account", "My account"),
            ProtectedRouteExpectation("/account/addresses", "My address"),
            ProtectedRouteExpectation("/committees/manage", "Committee Manager"),
            ProtectedRouteExpectation("/events/create", "Create Event"),
            ProtectedRouteExpectation("/events/edit/$eventId", "Edit Event"),
            ProtectedRouteExpectation("/events/signups/$eventId", "Respondents"),
            ProtectedRouteExpectation("/members/manage", "Member Manager"),
            ProtectedRouteExpectation("/contributions/manage", "Contribution Manager"),
            ProtectedRouteExpectation("/addresses/manage", "Address Manager"),
            ProtectedRouteExpectation("/recovery/manage", "Recovery Manager")
        )

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            expectations.forEach { expectation ->
                page.navigate("$frontendUrl${expectation.path}")
                waitFor(
                    timeoutMs = 12_000,
                    onTimeoutMessage = {
                        "Expected protected route ${expectation.path} to render '${expectation.expectedText}'"
                    }
                ) {
                    page.url().contains(expectation.expectedUrlContains) &&
                        page.locator("body").innerText().lowercase().contains(expectation.expectedText.lowercase())
                }
            }
        }
    }

    @Test
    fun `admin can open admin-only route`() {
        val admin = userFactory.createUserWithRole(Role.ADMIN, enabled = true)

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, admin.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            page.navigate("$frontendUrl/management/jobs")
            waitFor(
                timeoutMs = 12_000,
                onTimeoutMessage = { "Expected admin route /management/jobs to render job manager page" }
            ) {
                page.url().contains("/management/jobs") &&
                    page.locator("body").innerText().lowercase().contains("job manager")
            }
        }
    }

    private data class ProtectedRouteExpectation(
        val path: String,
        val expectedText: String,
        val expectedUrlContains: String = path
    )

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
