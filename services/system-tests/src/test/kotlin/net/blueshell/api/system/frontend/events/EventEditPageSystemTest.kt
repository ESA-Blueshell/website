package net.blueshell.api.system.frontend.events

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
class EventEditPageSystemTest : PlaywrightTestBase() {

    @Test
    fun `edit page updates event details`() {
        val member = TestHelper.registerActivateAndPromote("COMMITTEE")
        val committeeId = TestHelper.createCommittee(name = "Edit Committee ${System.currentTimeMillis()}")
        TestHelper.addCommitteeMember(committeeId, member.username)
        val eventId = TestHelper.createEvent(
            committeeId = committeeId,
            title = "Editable Event ${System.currentTimeMillis()}",
            approved = false,
        )
        val updatedTitle = "Updated Event ${System.currentTimeMillis()}"
        val updatedLocation = "New Location"
        val updatedDescription = "Updated event description"

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)
        assertThat(loginStatus).isEqualTo(200)

        EventFormHelper.openEditPage(page, frontendUrl, eventId)
        EventFormHelper.fillRequiredFields(page, updatedTitle, updatedLocation, updatedDescription)

        val response = page.waitForResponse(
            Predicate { r -> r.request().method() == "PUT" && r.url().contains("/events/$eventId") },
        ) {
            EventFormHelper.submit(page)
        }
        assertThat(response.status()).isEqualTo(200)

        val updated = waitForEvent(eventId) { it.title == updatedTitle }
        assertThat(updated.title).isEqualTo(updatedTitle)
        assertThat(updated.location).isEqualTo(updatedLocation)
        assertThat(updated.description).isEqualTo(updatedDescription)
    }

    @Test
    fun `member edit moves approved event back to awaiting approval`() {
        val member = TestHelper.registerActivateAndPromote("COMMITTEE")
        val committeeId = TestHelper.createCommittee(name = "Reapprove Committee ${System.currentTimeMillis()}")
        TestHelper.addCommitteeMember(committeeId, member.username)
        val originalTitle = "Needs Reapproval ${System.currentTimeMillis()}"
        val eventId = TestHelper.createEvent(
            committeeId = committeeId,
            title = originalTitle,
            approved = true,
        )

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)
        assertThat(loginStatus).isEqualTo(200)

        EventFormHelper.openEditPage(page, frontendUrl, eventId)
        EventFormHelper.fillRequiredFields(
            page = page,
            title = originalTitle,
            location = "Campus",
            description = "Updated by committee member",
        )

        val response = page.waitForResponse(
            Predicate { r -> r.request().method() == "PUT" && r.url().contains("/events/$eventId") },
        ) {
            EventFormHelper.submit(page)
        }
        assertThat(response.status()).isEqualTo(200)

        val updated = waitForEvent(eventId) { it.description == "Updated by committee member" }
        assertThat(updated.approved).isFalse()
        assertThat(updated.description).isEqualTo("Updated by committee member")
    }

    @Test
    fun `board can approve from edit page`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val committeeId = TestHelper.createCommittee(name = "Board Edit Committee ${System.currentTimeMillis()}")
        val eventId = TestHelper.createEvent(
            committeeId = committeeId,
            title = "Board Edit Approval ${System.currentTimeMillis()}",
            approved = false,
        )

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        EventFormHelper.openEditPage(page, frontendUrl, eventId)
        EventFormHelper.setApproved(page, approved = true)

        val response = page.waitForResponse(
            Predicate { r -> r.request().method() == "PUT" && r.url().contains("/events/$eventId") },
        ) {
            EventFormHelper.submit(page)
        }
        assertThat(response.status()).isEqualTo(200)

        assertThat(waitForEvent(eventId) { it.approved }.approved).isTrue()
    }

    @Test
    fun `events page fetches banner after editing event banner`() {
        val member = TestHelper.registerActivateAndPromote("COMMITTEE")
        val committeeId = TestHelper.createCommittee(name = "Banner Edit Committee ${System.currentTimeMillis()}")
        TestHelper.addCommitteeMember(committeeId, member.username)
        val eventId = TestHelper.createEvent(
            committeeId = committeeId,
            title = "Edit Banner Event ${System.currentTimeMillis()}",
            approved = true,
        )

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)
        assertThat(loginStatus).isEqualTo(200)

        EventFormHelper.openEditPage(page, frontendUrl, eventId)
        EventFormHelper.uploadBanner(page, EVENT_BANNER_PATH)

        val updateResponse = page.waitForResponse(
            Predicate { r -> r.request().method() == "PUT" && r.url().contains("/events/$eventId") },
        ) {
            EventFormHelper.submit(page)
        }
        assertThat(updateResponse.status()).isEqualTo(200)

        // Open the events page in a fresh page so the banner-fetch
        // response is observable without cache interference.
        val freshContext = context.browser().newContext()
        try {
            val freshPage = freshContext.newPage()
            val freshLoginStatus = AuthHelper.submitLogin(freshPage, frontendUrl, member.username, member.password)
            assertThat(freshLoginStatus).isEqualTo(200)

            val bannerResponse = freshPage.waitForResponse(
                Predicate { r -> r.request().method() == "GET" && r.url().contains("/events/$eventId/banners") },
            ) {
                freshPage.navigate("$frontendUrl/events")
            }
            assertThat(bannerResponse.status()).isEqualTo(200)
        } finally {
            freshContext.close()
        }
    }

    private fun waitForEvent(eventId: Long, predicate: (TestHelper.EventRow) -> Boolean): TestHelper.EventRow {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val row = TestHelper.findEvent(eventId)
            if (row != null && predicate(row)) return row
            Thread.sleep(200)
        }
        throw AssertionError("Expected event $eventId to satisfy predicate within 10s")
    }

    private companion object {
        const val EVENT_BANNER_PATH = "../frontend/public/favicon.png"
    }
}
