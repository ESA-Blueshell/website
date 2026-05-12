package net.blueshell.api.system.frontend.events

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.ApiApplication
import net.blueshell.api.config.TestCleanUpListener
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.EventFormHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestExecutionListeners
import java.util.function.Predicate

@Tag("system")
@ActiveProfiles("test")
@TestExecutionListeners(
    listeners = [TestCleanUpListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS,
)
@SpringBootTest(
    classes = [ApiApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = ["server.port=8080", "app.jobs.auto-dispatch=true"],
)
class EventCreatePageSystemTest : PlaywrightTestBase() {

    @Test
    fun `committee member can only select own committees on event create`() {
        val member = TestHelper.registerActivateAndPromote("COMMITTEE")
        val ownName = "Own Committee ${System.currentTimeMillis()}"
        val otherName = "Other Committee ${System.currentTimeMillis()}"
        val ownId = TestHelper.createCommittee(name = ownName)
        TestHelper.createCommittee(name = otherName)
        TestHelper.addCommitteeMember(ownId, member.username)

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)
        assertThat(loginStatus).isEqualTo(200)

        EventFormHelper.openCreatePage(page, frontendUrl)
        EventFormHelper.openCommitteeSelect(page)

        assertThat(page.getByText(ownName, Page.GetByTextOptions().setExact(true)).count()).isGreaterThan(0)
        assertThat(page.getByText(otherName, Page.GetByTextOptions().setExact(true)).count()).isEqualTo(0)
    }

    @Test
    fun `committee member created events stay unapproved`() {
        val member = TestHelper.registerActivateAndPromote("COMMITTEE")
        val committeeName = "Member Committee ${System.currentTimeMillis()}"
        val committeeId = TestHelper.createCommittee(name = committeeName)
        TestHelper.addCommitteeMember(committeeId, member.username)
        val eventTitle = "Committee Event ${System.currentTimeMillis()}"

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)
        assertThat(loginStatus).isEqualTo(200)

        EventFormHelper.openCreatePage(page, frontendUrl)
        EventFormHelper.fillRequiredFields(
            page = page,
            title = eventTitle,
            location = "Campus",
            description = "Committee created event",
        )
        EventFormHelper.selectCommittee(page, committeeName)

        val response = page.waitForResponse(
            Predicate { r ->
                r.request().method() == "POST" &&
                    r.url().contains("/events") &&
                    !r.url().contains("/events/banners")
            },
        ) {
            EventFormHelper.submit(page)
        }
        assertThat(response.status()).isEqualTo(201)

        val created = waitForEventByTitle(eventTitle)
        assertThat(created.approved).isFalse()
        assertThat(created.committeeId).isEqualTo(committeeId)
    }

    @Test
    fun `board can create approved event for any committee`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val nameA = "A Committee ${System.currentTimeMillis()}"
        val nameB = "B Committee ${System.currentTimeMillis()}"
        val committeeAId = TestHelper.createCommittee(name = nameA)
        val committeeBId = TestHelper.createCommittee(name = nameB)
        val eventTitle = "Board Created Event ${System.currentTimeMillis()}"

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        EventFormHelper.openCreatePage(page, frontendUrl)
        EventFormHelper.fillRequiredFields(
            page = page,
            title = eventTitle,
            location = "Meeting Room",
            description = "Board created event",
        )
        EventFormHelper.selectCommittee(page, nameB)
        EventFormHelper.setApproved(page, approved = true)

        val response = page.waitForResponse(
            Predicate { r ->
                r.request().method() == "POST" &&
                    r.url().contains("/events") &&
                    !r.url().contains("/events/banners")
            },
        ) {
            EventFormHelper.submit(page)
        }
        assertThat(response.status()).isEqualTo(201)

        val created = waitForEventByTitle(eventTitle)
        assertThat(created.approved).isTrue()
        assertThat(created.committeeId).isEqualTo(committeeBId)
        assertThat(created.committeeId).isNotEqualTo(committeeAId)
    }

    @Test
    fun `events page fetches banner for newly created event`() {
        val member = TestHelper.registerActivateAndPromote("COMMITTEE")
        val committeeName = "Banner Committee ${System.currentTimeMillis()}"
        val committeeId = TestHelper.createCommittee(name = committeeName)
        TestHelper.addCommitteeMember(committeeId, member.username)
        val eventTitle = "Banner Event ${System.currentTimeMillis()}"

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)
        assertThat(loginStatus).isEqualTo(200)

        EventFormHelper.openCreatePage(page, frontendUrl)
        EventFormHelper.fillRequiredFields(
            page = page,
            title = eventTitle,
            location = "Campus",
            description = "Event with banner upload",
        )
        EventFormHelper.selectCommittee(page, committeeName)
        EventFormHelper.uploadBanner(page, EVENT_BANNER_PATH)

        val createResponse = page.waitForResponse(
            Predicate { r ->
                r.request().method() == "POST" &&
                    r.url().contains("/events") &&
                    !r.url().contains("/events/banners")
            },
        ) {
            EventFormHelper.submit(page)
        }
        assertThat(createResponse.status()).isEqualTo(201)

        val created = waitForEventByTitle(eventTitle)

        // Fresh context to observe the events-page banner fetch
        // without cache / cookie interference.
        val freshContext = context.browser().newContext()
        try {
            val freshPage = freshContext.newPage()
            val freshLoginStatus = AuthHelper.submitLogin(freshPage, frontendUrl, member.username, member.password)
            assertThat(freshLoginStatus).isEqualTo(200)

            val bannerResponse = freshPage.waitForResponse(
                Predicate { r ->
                    r.request().method() == "GET" &&
                        r.url().contains("/events/${created.id}/banners")
                },
            ) {
                freshPage.navigate("$frontendUrl/events")
            }
            assertThat(bannerResponse.status()).isEqualTo(200)
        } finally {
            freshContext.close()
        }
    }

    @Test
    fun `board can approve event from events page`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val committeeName = "Approve Committee ${System.currentTimeMillis()}"
        val committeeId = TestHelper.createCommittee(name = committeeName)
        TestHelper.addCommitteeMember(committeeId, board.username)
        val eventTitle = "Approve From Events Page ${System.currentTimeMillis()}"
        val eventId = TestHelper.createEvent(
            committeeId = committeeId,
            title = eventTitle,
            approved = false,
        )

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        page.navigate("$frontendUrl/events")
        page.getByText(eventTitle, Page.GetByTextOptions().setExact(false)).first().waitFor()

        val response = page.waitForResponse(
            Predicate { r ->
                r.request().method() == "PUT" &&
                    r.url().contains("/events/$eventId/approve") &&
                    r.url().contains("approved=true")
            },
        ) {
            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Awaiting approval").setExact(false),
            ).first().click()
        }
        assertThat(response.status()).isEqualTo(200)

        waitForEventState(eventId) { it.approved }
    }

    @Test
    fun `sign-up deadline and limit fields are hidden when sign-up is disabled`() {
        val member = TestHelper.registerActivateAndPromote("COMMITTEE")
        val committeeName = "SignUp Hidden Committee ${System.currentTimeMillis()}"
        val committeeId = TestHelper.createCommittee(name = committeeName)
        TestHelper.addCommitteeMember(committeeId, member.username)

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)
        assertThat(loginStatus).isEqualTo(200)

        EventFormHelper.openCreatePage(page, frontendUrl)

        assertThat(EventFormHelper.signUpDeadlineInput(page).count()).isEqualTo(0)
        assertThat(EventFormHelper.signUpLimitInput(page).count()).isEqualTo(0)
    }

    @Test
    fun `sign-up deadline and limit fields appear when sign-up is enabled`() {
        val member = TestHelper.registerActivateAndPromote("COMMITTEE")
        val committeeName = "SignUp Visible Committee ${System.currentTimeMillis()}"
        val committeeId = TestHelper.createCommittee(name = committeeName)
        TestHelper.addCommitteeMember(committeeId, member.username)

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)
        assertThat(loginStatus).isEqualTo(200)

        EventFormHelper.openCreatePage(page, frontendUrl)
        EventFormHelper.enableSignUp(page)

        EventFormHelper.signUpDeadlineInput(page).waitFor()
        EventFormHelper.signUpLimitInput(page).waitFor()
    }

    @Test
    fun `creating event with sign-up limit persists the limit`() {
        val member = TestHelper.registerActivateAndPromote("COMMITTEE")
        val committeeName = "Limit Committee ${System.currentTimeMillis()}"
        val committeeId = TestHelper.createCommittee(name = committeeName)
        TestHelper.addCommitteeMember(committeeId, member.username)
        val eventTitle = "Limited Signup Event ${System.currentTimeMillis()}"

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)
        assertThat(loginStatus).isEqualTo(200)

        EventFormHelper.openCreatePage(page, frontendUrl)
        EventFormHelper.fillRequiredFields(
            page = page,
            title = eventTitle,
            location = "Campus",
            description = "Event with signup limit",
        )
        EventFormHelper.selectCommittee(page, committeeName)
        EventFormHelper.enableSignUp(page)
        EventFormHelper.signUpDeadlineInput(page).waitFor()
        EventFormHelper.setSignUpLimit(page, 42)

        val response = page.waitForResponse(
            Predicate { r ->
                r.request().method() == "POST" &&
                    r.url().contains("/events") &&
                    !r.url().contains("/events/banners")
            },
        ) {
            EventFormHelper.submit(page)
        }
        assertThat(response.status()).isEqualTo(201)

        val created = waitForEventByTitle(eventTitle)
        assertThat(created.signUpLimit).isEqualTo(42)
        assertThat(created.signUp).isTrue()
    }

    @Test
    fun `clearing sign-up limit field sends no limit to the API`() {
        val member = TestHelper.registerActivateAndPromote("COMMITTEE")
        val committeeName = "No Limit Committee ${System.currentTimeMillis()}"
        val committeeId = TestHelper.createCommittee(name = committeeName)
        TestHelper.addCommitteeMember(committeeId, member.username)
        val eventTitle = "Unlimited Signup Event ${System.currentTimeMillis()}"

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)
        assertThat(loginStatus).isEqualTo(200)

        EventFormHelper.openCreatePage(page, frontendUrl)
        EventFormHelper.fillRequiredFields(
            page = page,
            title = eventTitle,
            location = "Campus",
            description = "Event with no signup limit",
        )
        EventFormHelper.selectCommittee(page, committeeName)
        EventFormHelper.enableSignUp(page)

        val response = page.waitForResponse(
            Predicate { r ->
                r.request().method() == "POST" &&
                    r.url().contains("/events") &&
                    !r.url().contains("/events/banners")
            },
        ) {
            EventFormHelper.submit(page)
        }
        assertThat(response.status()).isEqualTo(201)

        val created = waitForEventByTitle(eventTitle)
        assertThat(created.signUp).isTrue()
        assertThat(created.signUpLimit).isNull()
    }

    private fun waitForEventByTitle(title: String): TestHelper.EventRow {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val row = TestHelper.findEventByTitle(title)
            if (row != null) return row
            Thread.sleep(200)
        }
        throw AssertionError("Expected event with title '$title' within 10s")
    }

    private fun waitForEventState(eventId: Long, predicate: (TestHelper.EventRow) -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val row = TestHelper.findEvent(eventId)
            if (row != null && predicate(row)) return
            Thread.sleep(200)
        }
        throw AssertionError("Expected event $eventId to satisfy predicate within 10s")
    }

    private companion object {
        const val EVENT_BANNER_PATH = "../frontend/public/favicon.png"
    }
}
