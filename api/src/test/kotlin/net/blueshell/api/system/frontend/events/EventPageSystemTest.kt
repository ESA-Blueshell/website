package net.blueshell.api.system.frontend.events

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.repository.EventRepository
import net.blueshell.api.domain.event.persistence.repository.EventSignUpRepository
import net.blueshell.api.factory.committee.persistence.CommitteeFactory
import net.blueshell.api.factory.event.persistence.EventFactory
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.EventPageHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.function.Predicate

@Tag("system")
class EventPageSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var userFactory: UserFactory

    @Autowired
    private lateinit var committeeFactory: CommitteeFactory

    @Autowired
    private lateinit var eventFactory: EventFactory

    @Autowired
    private lateinit var eventRepository: EventRepository

    @Autowired
    private lateinit var eventSignUpRepository: EventSignUpRepository

    @Test
    fun `board can approve event from event card`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val committee = committeeFactory.create(name = "Approval Committee ${System.currentTimeMillis()}")
        val event = createCurrentMonthEvent(
            committee = committee,
            approved = false,
            signUp = false,
            title = "Card Approval Event ${System.currentTimeMillis()}"
        )
        val eventId = checkNotNull(event.id) { "Expected event id" }

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            EventPageHelper.open(page, frontendUrl)
            EventPageHelper.waitForEventVisible(page, event.title)

            val response = page.waitForResponse(
                { r ->
                    r.request().method() == "PUT" &&
                            r.url().contains("/events/$eventId/approve") &&
                            r.url().contains("approved=true")
                }
            ) {
                EventPageHelper.clickApproveButton(page, event.title, "Awaiting approval")
            }
            assertThat(response.status()).isEqualTo(200)
        }

        waitFor(
            onTimeoutMessage = { "Expected event $eventId to become approved from event card action" }
        ) {
            eventRepository.findById(eventId).orElseThrow().approved
        }
    }

    @Test
    fun `committee member can delete event from event card`() {
        val member = userFactory.createUserWithRole(Role.COMMITTEE, enabled = true)
        val committee = committeeFactory.create(name = "Delete Card Committee ${System.currentTimeMillis()}")
        committeeFactory.createMember(committee, member)
        val event = createCurrentMonthEvent(
            committee = committee,
            approved = true,
            signUp = false,
            title = "Delete Card Event ${System.currentTimeMillis()}"
        )
        val eventId = checkNotNull(event.id) { "Expected event id" }

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            EventPageHelper.open(page, frontendUrl)
            EventPageHelper.waitForEventVisible(page, event.title)

            val response = page.waitForResponse(
                { r -> r.request().method() == "DELETE" && r.url().contains("/events/$eventId") }
            ) {
                EventPageHelper.clickCardIcon(page, event.title, "mdi-delete")
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Delete").setExact(true)
                ).click()
            }
            assertThat(response.status()).isEqualTo(204)
        }

        waitFor(
            onTimeoutMessage = { "Expected event $eventId to be deleted from event card action" }
        ) {
            eventRepository.findById(eventId).isEmpty
        }
    }

    @Test
    fun `logged-in user can create update and delete event sign-up`() {
        val member = userFactory.createUserWithRole(Role.MEMBER, enabled = true)
        val committee = committeeFactory.create(name = "Signup Member Committee ${System.currentTimeMillis()}")
        val event = createCurrentMonthEvent(
            committee = committee,
            approved = true,
            signUp = true,
            title = "Member Signup Event ${System.currentTimeMillis()}"
        )
        val eventId = checkNotNull(event.id) { "Expected event id" }
        val memberId = checkNotNull(member.id) { "Expected member id" }

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            EventPageHelper.open(page, frontendUrl)
            EventPageHelper.waitForEventVisible(page, event.title)

            eventCardSignUpButton(page, event.title, "Sign up").click()

            val createResponse = page.waitForResponse(
                Predicate { r ->
                    r.request().method() == "POST" &&
                            r.url().contains("/events/$eventId/signups")
                }
            ) {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Save sign-up").setExact(false)
                ).click()
            }
            assertThat(createResponse.status()).isEqualTo(201)

            waitFor(
                onTimeoutMessage = { "Expected user sign-up to be persisted for user=$memberId event=$eventId" }
            ) {
                eventSignUpRepository.findByUser_IdAndEvent_Id(memberId, eventId).isPresent
            }

            eventCardSignUpButton(page, event.title, "Edit sign-up").click()

            val updateResponse = page.waitForResponse(
                Predicate { r ->
                    r.request().method() == "PUT" &&
                            r.url().contains("/events/$eventId/signups")
                }
            ) {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Update sign-up").setExact(false)
                ).click()
            }
            assertThat(updateResponse.status()).isEqualTo(200)

            val existingSignUpId = eventSignUpRepository.findByUser_IdAndEvent_Id(memberId, eventId).orElseThrow().id
            checkNotNull(existingSignUpId) { "Expected persisted sign-up id after update" }

            eventCardSignUpButton(page, event.title, "Edit sign-up").click()

            val deleteResponse = page.waitForResponse(
                Predicate { r ->
                    r.request().method() == "DELETE" &&
                            r.url().contains("/events/signups/$existingSignUpId")
                }
            ) {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Delete sign-up").setExact(false)
                ).click()
            }
            assertThat(deleteResponse.status()).isEqualTo(204)
        }

        waitFor(
            onTimeoutMessage = { "Expected user sign-up to be deleted for user=$memberId event=$eventId" }
        ) {
            eventSignUpRepository.findByUser_IdAndEvent_Id(memberId, eventId).isEmpty
        }
    }

    @Test
    fun `guest can create update and delete event sign-up`() {
        val committee = committeeFactory.create(name = "Signup Guest Committee ${System.currentTimeMillis()}")
        val event = createCurrentMonthEvent(
            committee = committee,
            approved = true,
            signUp = true,
            title = "Guest Signup Event ${System.currentTimeMillis()}"
        )
        val eventId = checkNotNull(event.id) { "Expected event id" }
        val updatedGuestName = "Guest Updated ${System.currentTimeMillis()}"
        var guestSignUpId: Long? = null

        withPage { page ->
            EventPageHelper.open(page, frontendUrl)
            EventPageHelper.waitForEventVisible(page, event.title)

            eventCardSignUpButton(page, event.title, "Sign up").click()

            page.getByLabel("Full name*", Page.GetByLabelOptions().setExact(true)).fill("Guest Original")
            page.getByLabel("Discord username*", Page.GetByLabelOptions().setExact(true)).fill("guest_original")
            page.getByLabel("Email*", Page.GetByLabelOptions().setExact(true))
                .fill("guest${System.currentTimeMillis()}@example.com")
            page.getByLabel("Phone Number*", Page.GetByLabelOptions().setExact(true)).fill("+31612345678")

            val createResponse = page.waitForResponse(
                Predicate { r ->
                    r.request().method() == "POST" &&
                            r.url().contains("/events/$eventId/signups")
                }
            ) {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Save sign-up").setExact(false)
                ).click()
            }
            assertThat(createResponse.status()).isEqualTo(201)

            checkNotNull(createResponse.headerValue("x-guest-access-token")) {
                "Expected guest access token header after guest sign-up create"
            }
            val persistedAfterCreate = waitForEventSignUpByEvent(eventId)
            guestSignUpId = checkNotNull(persistedAfterCreate.id) { "Expected guest sign-up id" }

            eventCardSignUpButton(page, event.title, "Edit sign-up").click()
            page.getByLabel("Full name*", Page.GetByLabelOptions().setExact(true)).fill(updatedGuestName)

            val updateResponse = page.waitForResponse(
                { r ->
                    r.request().method() == "PUT" &&
                            r.url().contains("/events/$eventId/signups")
                }
            ) {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Update sign-up").setExact(false)
                ).click()
            }
            assertThat(updateResponse.status()).isEqualTo(200)

            waitFor(
                onTimeoutMessage = { "Expected guest sign-up name update to be persisted" }
            ) {
                eventSignUpRepository.findById(checkNotNull(guestSignUpId)).map { it.guest?.name == updatedGuestName }
                    .orElse(false)
            }

            eventCardSignUpButton(page, event.title, "Edit sign-up").click()

            val deleteResponse = page.waitForResponse(
                Predicate { r ->
                    r.request().method() == "DELETE" &&
                            r.url().contains("/events/signups/$guestSignUpId")
                }
            ) {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Delete sign-up").setExact(false)
                ).click()
            }
            assertThat(deleteResponse.status()).isEqualTo(204)
        }

        waitFor(
            onTimeoutMessage = { "Expected guest sign-up to be deleted for event=$eventId" }
        ) {
            eventSignUpRepository.findById(checkNotNull(guestSignUpId)).isEmpty
        }
    }

    private fun createCurrentMonthEvent(
        committee: net.blueshell.api.domain.committee.persistence.Committee,
        approved: Boolean,
        signUp: Boolean,
        title: String
    ): Event {
        val event = eventFactory.create(
            committee = committee,
            approved = approved,
            signUp = signUp,
            title = title
        )
        event.startTime = Instant.now().plusSeconds(2 * 3600)
        event.endTime = Instant.now().plusSeconds(3 * 3600)
        return eventRepository.saveAndFlush(event)
    }

    private fun eventCardSignUpButton(page: Page, eventTitle: String, labelContains: String) =
        EventPageHelper.eventCard(page, eventTitle).locator("button[aria-label*='$labelContains']").first()

    private fun waitForEventSignUpByEvent(eventId: Long) =
        waitForOptional(
            producer = { eventSignUpRepository.findByEvent_Id(eventId).stream().findFirst() },
            onTimeoutMessage = { "Expected at least one sign-up for event=$eventId" }
        )

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
