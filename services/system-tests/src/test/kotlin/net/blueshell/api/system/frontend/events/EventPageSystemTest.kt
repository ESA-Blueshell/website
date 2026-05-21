package net.blueshell.api.system.frontend.events

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.EventPageHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.function.Predicate

@Tag("system")
class EventPageSystemTest : PlaywrightTestBase() {

    @Test
    fun `board can approve event from event card`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val committeeId = TestHelper.createCommittee(name = "Approval Committee ${System.currentTimeMillis()}")
        val eventId = createCurrentMonthEvent(
            committeeId = committeeId,
            approved = false,
            signUp = false,
            title = "Card Approval Event ${System.currentTimeMillis()}",
        )

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        EventPageHelper.open(page, frontendUrl)
        EventPageHelper.waitForEventCardVisible(page, eventId)

        val response = page.waitForResponse(
            { r ->
                r.request().method() == "PUT" &&
                    r.url().contains("/events/$eventId/approve") &&
                    r.url().contains("approved=true")
            },
        ) {
            EventPageHelper.clickApproveButton(page, eventId)
        }
        assertThat(response.status()).isEqualTo(200)

        pollFor("event $eventId becomes approved from event card action") {
            TestHelper.findEvent(eventId)?.approved == true
        }
    }

    @Test
    fun `committee member can delete event from event card`() {
        val member = TestHelper.registerActivateAndPromote("COMMITTEE")
        val committeeId = TestHelper.createCommittee(name = "Delete Card Committee ${System.currentTimeMillis()}")
        TestHelper.addCommitteeMember(committeeId, member.username)
        val eventId = createCurrentMonthEvent(
            committeeId = committeeId,
            approved = true,
            signUp = false,
            title = "Delete Card Event ${System.currentTimeMillis()}",
        )

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)
        assertThat(loginStatus).isEqualTo(200)

        EventPageHelper.open(page, frontendUrl)
        EventPageHelper.waitForEventCardVisible(page, eventId)

        val response = page.waitForResponse(
            { r -> r.request().method() == "DELETE" && r.url().contains("/events/$eventId") },
        ) {
            EventPageHelper.clickDeleteEventButton(page, eventId)
            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Delete").setExact(true),
            ).click()
        }
        assertThat(response.status()).isEqualTo(204)

        pollFor("event $eventId soft-deleted from event card action") {
            TestHelper.findEvent(eventId) == null
        }
    }

    @Test
    fun `logged-in user can create event sign-up`() {
        val member = TestHelper.registerActivateAndPromote("MEMBER")
        val memberId = TestHelper.findUser(member.username)!!.id
        val committeeId = TestHelper.createCommittee(name = "Signup Member Create Committee ${System.currentTimeMillis()}")
        val eventId = createCurrentMonthEvent(
            committeeId = committeeId,
            approved = true,
            signUp = true,
            title = "Member Signup Create Event ${System.currentTimeMillis()}",
        )

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)
        assertThat(loginStatus).isEqualTo(200)

        EventPageHelper.open(page, frontendUrl)
        EventPageHelper.waitForEventCardVisible(page, eventId)
        openSignUpForm(page, eventId)

        val createResponse = page.waitForResponse(
            Predicate { r ->
                r.request().method() == "POST" &&
                    r.url().contains("/events/$eventId/signups")
            },
        ) {
            EventPageHelper.clickSubmitSignUpButton(page, eventId)
        }
        assertThat(createResponse.status()).isEqualTo(201)

        pollFor("user sign-up persisted for user=$memberId event=$eventId") {
            TestHelper.findUserEventSignUp(eventId, memberId) != null
        }
    }

    @Test
    fun `logged-in user can update existing event sign-up`() {
        val member = TestHelper.registerActivateAndPromote("MEMBER")
        val memberId = TestHelper.findUser(member.username)!!.id
        val committeeId = TestHelper.createCommittee(name = "Signup Member Update Committee ${System.currentTimeMillis()}")
        val eventId = createCurrentMonthEvent(
            committeeId = committeeId,
            approved = true,
            signUp = true,
            title = "Member Signup Update Event ${System.currentTimeMillis()}",
        )
        val surveyId = TestHelper.attachSurveyToEvent(eventId)
        TestHelper.createQuestion(surveyId, idx = 0, type = "OPEN", label = "Anything else?")
        val existingSignUpId = TestHelper.createUserEventSignUp(eventId, memberId)

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)
        assertThat(loginStatus).isEqualTo(200)

        EventPageHelper.open(page, frontendUrl)
        EventPageHelper.waitForEventCardVisible(page, eventId)
        openSignUpForm(page, eventId)
        EventPageHelper.waitForSignUpMode(page, eventId, "update")

        val updateResponse = page.waitForResponse(
            Predicate { r ->
                r.request().method() == "PUT" &&
                    r.url().contains("/events/$eventId/signups")
            },
        ) {
            EventPageHelper.clickSubmitSignUpButton(page, eventId)
        }
        assertThat(updateResponse.status()).isEqualTo(200)

        pollFor("user sign-up id $existingSignUpId persists after update for user=$memberId event=$eventId") {
            TestHelper.findUserEventSignUp(eventId, memberId) == existingSignUpId
        }
    }

    @Test
    fun `logged-in user can delete existing event sign-up`() {
        val member = TestHelper.registerActivateAndPromote("MEMBER")
        val memberId = TestHelper.findUser(member.username)!!.id
        val committeeId = TestHelper.createCommittee(name = "Signup Member Delete Committee ${System.currentTimeMillis()}")
        val eventId = createCurrentMonthEvent(
            committeeId = committeeId,
            approved = true,
            signUp = true,
            title = "Member Signup Delete Event ${System.currentTimeMillis()}",
        )
        val surveyId = TestHelper.attachSurveyToEvent(eventId)
        TestHelper.createQuestion(surveyId, idx = 0, type = "OPEN", label = "Anything else?")
        val existingSignUpId = TestHelper.createUserEventSignUp(eventId, memberId)

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)
        assertThat(loginStatus).isEqualTo(200)

        EventPageHelper.open(page, frontendUrl)
        EventPageHelper.waitForEventCardVisible(page, eventId)
        openSignUpForm(page, eventId)
        EventPageHelper.waitForSignUpMode(page, eventId, "update")
        EventPageHelper.deleteSignUpButton(page, eventId).waitFor()

        val deleteResponse = page.waitForResponse(
            Predicate { r ->
                r.request().method() == "DELETE" &&
                    r.url().contains("/events/signups/$existingSignUpId")
            },
        ) {
            EventPageHelper.clickDeleteSignUpButton(page, eventId)
        }
        assertThat(deleteResponse.status()).isEqualTo(204)

        pollFor("user sign-up $existingSignUpId removed for event=$eventId") {
            TestHelper.findUserEventSignUp(eventId, memberId) == null
        }
    }

    @Test
    fun `guest can create event sign-up`() {
        val committeeId = TestHelper.createCommittee(name = "Signup Guest Create Committee ${System.currentTimeMillis()}")
        val eventId = createCurrentMonthEvent(
            committeeId = committeeId,
            approved = true,
            signUp = true,
            title = "Guest Signup Create Event ${System.currentTimeMillis()}",
        )
        val guestName = "Guest Original"
        val guestDiscord = "guest_original"

        EventPageHelper.open(page, frontendUrl)
        EventPageHelper.waitForEventCardVisible(page, eventId)
        openSignUpForm(page, eventId)

        page.getByLabel("Full name*", Page.GetByLabelOptions().setExact(true)).fill(guestName)
        page.getByLabel("Discord username*", Page.GetByLabelOptions().setExact(true)).fill(guestDiscord)
        page.getByLabel("Email*", Page.GetByLabelOptions().setExact(true))
            .fill("guest${System.currentTimeMillis()}@example.com")
        page.getByLabel("Phone Number*", Page.GetByLabelOptions().setExact(true)).fill("+31612345678")

        val createResponse = page.waitForResponse(
            Predicate { r ->
                r.request().method() == "POST" &&
                    r.url().contains("/events/$eventId/signups")
            },
        ) {
            EventPageHelper.clickSubmitSignUpButton(page, eventId)
        }
        assertThat(createResponse.status()).isEqualTo(201)
        checkNotNull(createResponse.headerValue("x-guest-access-token")) {
            "Expected guest access token header after guest sign-up create"
        }

        pollFor("guest sign-up for event=$eventId reflects submitted name/discord") {
            val signUp = TestHelper.findGuestEventSignUp(eventId)
            signUp != null && signUp.name == guestName && signUp.discord == guestDiscord
        }
    }

    @Test
    fun `guest can update existing event sign-up`() {
        val committeeId = TestHelper.createCommittee(name = "Signup Guest Update Committee ${System.currentTimeMillis()}")
        val eventId = createCurrentMonthEvent(
            committeeId = committeeId,
            approved = true,
            signUp = true,
            title = "Guest Signup Update Event ${System.currentTimeMillis()}",
        )
        val surveyId = TestHelper.attachSurveyToEvent(eventId)
        TestHelper.createQuestion(surveyId, idx = 0, type = "OPEN", label = "Anything else?")
        val originalGuestName = "Guest Original"
        val originalGuestDiscord = "guest_original"
        val originalGuestEmail = "guest-original-${System.currentTimeMillis()}@example.com"
        val originalGuestPhone = "+31612345678"
        val updatedGuestName = "Guest Updated ${System.currentTimeMillis()}"
        val updatedGuestDiscord = "guest_updated_${System.currentTimeMillis()}"
        val updatedGuestEmail = "guest-updated-${System.currentTimeMillis()}@example.com"
        val updatedGuestPhone = "+31687654321"

        EventPageHelper.open(page, frontendUrl)
        EventPageHelper.waitForEventCardVisible(page, eventId)
        openSignUpForm(page, eventId)
        fillGuestDetails(
            page = page,
            name = originalGuestName,
            discord = originalGuestDiscord,
            email = originalGuestEmail,
            phoneNumber = originalGuestPhone,
        )

        val createResponse = page.waitForResponse(
            Predicate { r ->
                r.request().method() == "POST" &&
                    r.url().contains("/events/$eventId/signups")
            },
        ) {
            EventPageHelper.clickSubmitSignUpButton(page, eventId)
        }
        assertThat(createResponse.status()).isEqualTo(201)
        checkNotNull(createResponse.headerValue("x-guest-access-token")) {
            "Expected guest access token header after guest sign-up create"
        }

        openSignUpForm(page, eventId)
        EventPageHelper.waitForSignUpMode(page, eventId, "update")
        fillGuestDetails(
            page = page,
            name = updatedGuestName,
            discord = updatedGuestDiscord,
            email = updatedGuestEmail,
            phoneNumber = updatedGuestPhone,
        )

        val updateResponse = page.waitForResponse(
            Predicate { r ->
                r.request().method() == "PUT" &&
                    r.url().contains("/events/$eventId/signups")
            },
        ) {
            EventPageHelper.clickSubmitSignUpButton(page, eventId)
        }
        assertThat(updateResponse.status()).isEqualTo(200)

        pollFor("guest sign-up reflects updated name/discord/email/phone for event=$eventId") {
            val signUp = TestHelper.findGuestEventSignUp(eventId)
            signUp != null &&
                signUp.name == updatedGuestName &&
                signUp.discord == updatedGuestDiscord &&
                signUp.email == updatedGuestEmail &&
                signUp.phoneNumber == updatedGuestPhone
        }
    }

    @Test
    fun `guest can delete existing event sign-up`() {
        val committeeId = TestHelper.createCommittee(name = "Signup Guest Delete Committee ${System.currentTimeMillis()}")
        val eventId = createCurrentMonthEvent(
            committeeId = committeeId,
            approved = true,
            signUp = true,
            title = "Guest Signup Delete Event ${System.currentTimeMillis()}",
        )
        val surveyId = TestHelper.attachSurveyToEvent(eventId)
        TestHelper.createQuestion(surveyId, idx = 0, type = "OPEN", label = "Anything else?")
        val originalGuestName = "Guest Delete"
        val originalGuestDiscord = "guest_delete"
        val originalGuestEmail = "guest-delete-${System.currentTimeMillis()}@example.com"

        EventPageHelper.open(page, frontendUrl)
        EventPageHelper.waitForEventCardVisible(page, eventId)
        openSignUpForm(page, eventId)
        fillGuestDetails(
            page = page,
            name = originalGuestName,
            discord = originalGuestDiscord,
            email = originalGuestEmail,
            phoneNumber = "+31612345678",
        )

        val createResponse = page.waitForResponse(
            Predicate { r ->
                r.request().method() == "POST" &&
                    r.url().contains("/events/$eventId/signups")
            },
        ) {
            EventPageHelper.clickSubmitSignUpButton(page, eventId)
        }
        assertThat(createResponse.status()).isEqualTo(201)
        checkNotNull(createResponse.headerValue("x-guest-access-token")) {
            "Expected guest access token header after guest sign-up create"
        }

        val existingSignUpId = pollForValue("persisted guest sign-up for event=$eventId before delete") {
            TestHelper.findGuestEventSignUp(eventId)?.id
        }

        openSignUpForm(page, eventId)
        EventPageHelper.waitForSignUpMode(page, eventId, "update")
        EventPageHelper.deleteSignUpButton(page, eventId).waitFor()
        EventPageHelper.clickDeleteSignUpButton(page, eventId)

        pollFor("guest sign-up $existingSignUpId removed for event=$eventId") {
            TestHelper.findGuestEventSignUp(eventId) == null
        }
    }

    private fun createCurrentMonthEvent(
        committeeId: Long,
        approved: Boolean,
        signUp: Boolean,
        title: String,
    ): Long {
        val startTime = Instant.now().plusSeconds(2 * 3600)
        val endTime = Instant.now().plusSeconds(3 * 3600)
        return TestHelper.createEvent(
            committeeId = committeeId,
            title = title,
            startTime = startTime,
            endTime = endTime,
            approved = approved,
            signUp = signUp,
        )
    }

    private fun openSignUpForm(page: Page, eventId: Long) {
        EventPageHelper.clickSignUpToggleButton(page, eventId)
        EventPageHelper.signUpForm(page, eventId).waitFor()
    }

    private fun fillGuestDetails(
        page: Page,
        name: String,
        discord: String,
        email: String,
        phoneNumber: String,
    ) {
        page.getByLabel("Full name*", Page.GetByLabelOptions().setExact(true)).fill(name)
        page.getByLabel("Discord username*", Page.GetByLabelOptions().setExact(true)).fill(discord)
        page.getByLabel("Email*", Page.GetByLabelOptions().setExact(true)).fill(email)
        page.getByLabel("Phone Number*", Page.GetByLabelOptions().setExact(true)).fill(phoneNumber)
    }

    private fun pollFor(description: String, timeoutMs: Long = 10_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(200)
        }
        throw AssertionError("Expected $description within ${timeoutMs}ms")
    }

    private fun <T : Any> pollForValue(
        description: String,
        timeoutMs: Long = 10_000,
        producer: () -> T?,
    ): T {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val value = producer()
            if (value != null) return value
            Thread.sleep(200)
        }
        throw AssertionError("Expected $description within ${timeoutMs}ms")
    }
}
