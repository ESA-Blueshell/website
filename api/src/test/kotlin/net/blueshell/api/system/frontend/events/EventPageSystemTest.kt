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
            EventPageHelper.waitForEventCardVisible(page, eventId)

            val response = page.waitForResponse(
                { r ->
                    r.request().method() == "PUT" &&
                            r.url().contains("/events/$eventId/approve") &&
                            r.url().contains("approved=true")
                }
            ) {
                EventPageHelper.clickApproveButton(page, eventId)
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
            EventPageHelper.waitForEventCardVisible(page, eventId)

            val response = page.waitForResponse(
                { r -> r.request().method() == "DELETE" && r.url().contains("/events/$eventId") }
            ) {
                EventPageHelper.clickDeleteEventButton(page, eventId)
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
    fun `logged-in user can create event sign-up`() {
        val member = userFactory.createUserWithRole(Role.MEMBER, enabled = true)
        val committee = committeeFactory.create(name = "Signup Member Create Committee ${System.currentTimeMillis()}")
        val event = createCurrentMonthEvent(
            committee = committee,
            approved = true,
            signUp = true,
            title = "Member Signup Create Event ${System.currentTimeMillis()}"
        )
        val eventId = checkNotNull(event.id) { "Expected event id" }
        val memberId = checkNotNull(member.id) { "Expected member id" }

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            EventPageHelper.open(page, frontendUrl)
            EventPageHelper.waitForEventCardVisible(page, eventId)
            openSignUpForm(page, eventId)

            val createResponse = page.waitForResponse(
                Predicate { r ->
                    r.request().method() == "POST" &&
                            r.url().contains("/events/$eventId/signups")
                }
            ) {
                EventPageHelper.clickSubmitSignUpButton(page, eventId)
            }
            assertThat(createResponse.status()).isEqualTo(201)
        }

        waitFor(
            onTimeoutMessage = { "Expected user sign-up to be persisted for user=$memberId event=$eventId" }
        ) {
            eventSignUpRepository.findByUser_IdAndEvent_Id(memberId, eventId).isPresent
        }
    }

    @Test
    fun `logged-in user can update existing event sign-up`() {
        val member = userFactory.createUserWithRole(Role.MEMBER, enabled = true)
        val committee = committeeFactory.create(name = "Signup Member Update Committee ${System.currentTimeMillis()}")
        val event = createCurrentMonthEvent(
            committee = committee,
            approved = true,
            signUp = true,
            title = "Member Signup Update Event ${System.currentTimeMillis()}"
        )
        val eventId = checkNotNull(event.id) { "Expected event id" }
        val memberId = checkNotNull(member.id) { "Expected member id" }
        val existingSignUp = eventFactory.createSignUp(event = event, user = member)
        val existingSignUpId = checkNotNull(existingSignUp.id) { "Expected existing member sign-up id" }

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            EventPageHelper.open(page, frontendUrl)
            EventPageHelper.waitForEventCardVisible(page, eventId)
            openSignUpForm(page, eventId)
            EventPageHelper.waitForSignUpMode(page, eventId, "update")

            val updateResponse = page.waitForResponse(
                Predicate { r ->
                    r.request().method() == "PUT" &&
                            r.url().contains("/events/$eventId/signups")
                }
            ) {
                EventPageHelper.clickSubmitSignUpButton(page, eventId)
            }
            assertThat(updateResponse.status()).isEqualTo(200)
        }

        waitFor(
            onTimeoutMessage = {
                "Expected updated user sign-up to remain persisted for user=$memberId event=$eventId signUp=$existingSignUpId"
            }
        ) {
            eventSignUpRepository.findByUser_IdAndEvent_Id(memberId, eventId)
                .map { it.id == existingSignUpId }
                .orElse(false)
        }
    }

    @Test
    fun `logged-in user can delete existing event sign-up`() {
        val member = userFactory.createUserWithRole(Role.MEMBER, enabled = true)
        val committee = committeeFactory.create(name = "Signup Member Delete Committee ${System.currentTimeMillis()}")
        val event = createCurrentMonthEvent(
            committee = committee,
            approved = true,
            signUp = true,
            title = "Member Signup Delete Event ${System.currentTimeMillis()}"
        )
        val eventId = checkNotNull(event.id) { "Expected event id" }
        val existingSignUp = eventFactory.createSignUp(event = event, user = member)
        val existingSignUpId = checkNotNull(existingSignUp.id) { "Expected existing member sign-up id" }

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, DEFAULT_PASSWORD)
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
                }
            ) {
                EventPageHelper.clickDeleteSignUpButton(page, eventId)
            }
            assertThat(deleteResponse.status()).isEqualTo(204)
        }

        waitFor(
            onTimeoutMessage = { "Expected user sign-up to be deleted for signUp=$existingSignUpId" }
        ) {
            eventSignUpRepository.findById(existingSignUpId).isEmpty
        }
    }

    @Test
    fun `guest can create event sign-up`() {
        val committee = committeeFactory.create(name = "Signup Guest Create Committee ${System.currentTimeMillis()}")
        val event = createCurrentMonthEvent(
            committee = committee,
            approved = true,
            signUp = true,
            title = "Guest Signup Create Event ${System.currentTimeMillis()}"
        )
        val eventId = checkNotNull(event.id) { "Expected event id" }
        val guestName = "Guest Original"
        val guestDiscord = "guest_original"

        withPage { page ->
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
                }
            ) {
                EventPageHelper.clickSubmitSignUpButton(page, eventId)
            }
            assertThat(createResponse.status()).isEqualTo(201)
            checkNotNull(createResponse.headerValue("x-guest-access-token")) {
                "Expected guest access token header after guest sign-up create"
            }
        }

        waitFor(
            onTimeoutMessage = { "Expected at least one guest sign-up for event=$eventId" }
        ) {
            val signUp = eventSignUpRepository.findByEvent_Id(eventId).stream().findFirst()
            signUp.isPresent && signUp.get().guest?.name == guestName && signUp.get().guest?.discord == guestDiscord
        }
    }

    @Test
    fun `guest can update existing event sign-up`() {
        val committee = committeeFactory.create(name = "Signup Guest Update Committee ${System.currentTimeMillis()}")
        val event = createCurrentMonthEvent(
            committee = committee,
            approved = true,
            signUp = true,
            title = "Guest Signup Update Event ${System.currentTimeMillis()}"
        )
        val eventId = checkNotNull(event.id) { "Expected event id" }
        val originalGuestName = "Guest Original"
        val originalGuestDiscord = "guest_original"
        val originalGuestEmail = "guest-original-${System.currentTimeMillis()}@example.com"
        val originalGuestPhone = "+31612345678"
        val updatedGuestName = "Guest Updated ${System.currentTimeMillis()}"
        val updatedGuestDiscord = "guest_updated_${System.currentTimeMillis()}"
        val updatedGuestEmail = "guest-updated-${System.currentTimeMillis()}@example.com"
        val updatedGuestPhone = "+31687654321"

        withPage { page ->
            EventPageHelper.open(page, frontendUrl)
            EventPageHelper.waitForEventCardVisible(page, eventId)
            openSignUpForm(page, eventId)
            fillGuestDetails(
                page = page,
                name = originalGuestName,
                discord = originalGuestDiscord,
                email = originalGuestEmail,
                phoneNumber = originalGuestPhone
            )

            val createResponse = page.waitForResponse(
                Predicate { r ->
                    r.request().method() == "POST" &&
                            r.url().contains("/events/$eventId/signups")
                }
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
                phoneNumber = updatedGuestPhone
            )

            val updateResponse = page.waitForResponse(
                Predicate { r ->
                    r.request().method() == "PUT" &&
                            r.url().contains("/events/$eventId/signups")
                }
            ) {
                EventPageHelper.clickSubmitSignUpButton(page, eventId)
            }
            assertThat(updateResponse.status()).isEqualTo(200)
        }

        waitFor(
            onTimeoutMessage = { "Expected guest sign-up name update to be persisted for event=$eventId" }
        ) {
            eventSignUpRepository.findByEvent_Id(eventId).stream().findFirst()
                .map { signUp ->
                    signUp.guest?.name == updatedGuestName &&
                            signUp.guest?.discord == updatedGuestDiscord &&
                            signUp.guest?.email == updatedGuestEmail &&
                            signUp.guest?.phoneNumber == updatedGuestPhone
                }
                .orElse(false)
        }
    }

    @Test
    fun `guest can delete existing event sign-up`() {
        val committee = committeeFactory.create(name = "Signup Guest Delete Committee ${System.currentTimeMillis()}")
        val event = createCurrentMonthEvent(
            committee = committee,
            approved = true,
            signUp = true,
            title = "Guest Signup Delete Event ${System.currentTimeMillis()}"
        )
        val eventId = checkNotNull(event.id) { "Expected event id" }
        val originalGuestName = "Guest Delete"
        val originalGuestDiscord = "guest_delete"
        val originalGuestEmail = "guest-delete-${System.currentTimeMillis()}@example.com"
        var existingSignUpId: Long? = null

        withPage { page ->
            EventPageHelper.open(page, frontendUrl)
            EventPageHelper.waitForEventCardVisible(page, eventId)
            openSignUpForm(page, eventId)
            fillGuestDetails(
                page = page,
                name = originalGuestName,
                discord = originalGuestDiscord,
                email = originalGuestEmail,
                phoneNumber = "+31612345678"
            )

            val createResponse = page.waitForResponse(
                Predicate { r ->
                    r.request().method() == "POST" &&
                            r.url().contains("/events/$eventId/signups")
                }
            ) {
                EventPageHelper.clickSubmitSignUpButton(page, eventId)
            }
            assertThat(createResponse.status()).isEqualTo(201)
            checkNotNull(createResponse.headerValue("x-guest-access-token")) {
                "Expected guest access token header after guest sign-up create"
            }
            existingSignUpId = waitForOptional(
                producer = { eventSignUpRepository.findByEvent_Id(eventId).stream().findFirst() },
                onTimeoutMessage = { "Expected persisted guest sign-up for event=$eventId before delete" }
            ).id

            openSignUpForm(page, eventId)
            EventPageHelper.waitForSignUpMode(page, eventId, "update")
            EventPageHelper.deleteSignUpButton(page, eventId).waitFor()
            EventPageHelper.clickDeleteSignUpButton(page, eventId)
        }

        waitFor(
            onTimeoutMessage = { "Expected guest sign-up to be deleted for signUp=$existingSignUpId" }
        ) {
            eventSignUpRepository.findById(checkNotNull(existingSignUpId)).isEmpty
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

    private fun openSignUpForm(page: Page, eventId: Long) {
        EventPageHelper.clickSignUpToggleButton(page, eventId)
        EventPageHelper.signUpForm(page, eventId).waitFor()
    }

    private fun fillGuestDetails(
        page: Page,
        name: String,
        discord: String,
        email: String,
        phoneNumber: String
    ) {
        page.getByLabel("Full name*", Page.GetByLabelOptions().setExact(true)).fill(name)
        page.getByLabel("Discord username*", Page.GetByLabelOptions().setExact(true)).fill(discord)
        page.getByLabel("Email*", Page.GetByLabelOptions().setExact(true)).fill(email)
        page.getByLabel("Phone Number*", Page.GetByLabelOptions().setExact(true)).fill(phoneNumber)
    }

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
