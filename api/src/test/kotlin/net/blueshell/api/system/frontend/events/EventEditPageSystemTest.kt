package net.blueshell.api.system.frontend.events

import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.persistence.repository.EventRepository
import net.blueshell.api.factory.committee.persistence.CommitteeFactory
import net.blueshell.api.factory.event.persistence.EventFactory
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.EventFormHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.function.Predicate

@Tag("system")
class EventEditPageSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var userFactory: UserFactory

    @Autowired
    private lateinit var committeeFactory: CommitteeFactory

    @Autowired
    private lateinit var eventFactory: EventFactory

    @Autowired
    private lateinit var eventRepository: EventRepository

    @Test
    fun `edit page updates event details`() {
        val member = userFactory.createUserWithRole(Role.COMMITTEE, enabled = true)
        val committee = committeeFactory.create(name = "Edit Committee ${System.currentTimeMillis()}")
        committeeFactory.createMember(committee, member)
        val event = createFutureEvent(
            committee = committee,
            approved = false,
            title = "Editable Event ${System.currentTimeMillis()}"
        )
        val eventId = checkNotNull(event.id) { "Expected event id" }
        val updatedTitle = "Updated Event ${System.currentTimeMillis()}"
        val updatedLocation = "New Location"
        val updatedDescription = "Updated event description"

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            EventFormHelper.openEditPage(page, frontendUrl, eventId)
            EventFormHelper.fillRequiredFields(page, updatedTitle, updatedLocation, updatedDescription)

            val response = page.waitForResponse(
                Predicate { r -> r.request().method() == "PUT" && r.url().contains("/events/$eventId") }
            ) {
                EventFormHelper.submit(page)
            }
            assertThat(response.status()).isEqualTo(200)
        }

        val updated = waitForEventById(eventId)
        assertThat(updated.title).isEqualTo(updatedTitle)
        assertThat(updated.location).isEqualTo(updatedLocation)
        assertThat(updated.description).isEqualTo(updatedDescription)
    }

    @Test
    fun `member edit moves approved event back to awaiting approval`() {
        val member = userFactory.createUserWithRole(Role.COMMITTEE, enabled = true)
        val committee = committeeFactory.create(name = "Reapprove Committee ${System.currentTimeMillis()}")
        committeeFactory.createMember(committee, member)
        val event = createFutureEvent(
            committee = committee,
            approved = true,
            title = "Needs Reapproval ${System.currentTimeMillis()}"
        )
        val eventId = checkNotNull(event.id) { "Expected event id" }

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            EventFormHelper.openEditPage(page, frontendUrl, eventId)
            EventFormHelper.fillRequiredFields(
                page = page,
                title = event.title,
                location = event.location ?: "Campus",
                description = "Updated by committee member"
            )

            val response = page.waitForResponse(
                Predicate { r -> r.request().method() == "PUT" && r.url().contains("/events/$eventId") }
            ) {
                EventFormHelper.submit(page)
            }
            assertThat(response.status()).isEqualTo(200)
        }

        val updated = waitForEventById(eventId)
        assertThat(updated.approved).isFalse()
        assertThat(updated.description).isEqualTo("Updated by committee member")
    }

    @Test
    fun `board can approve from edit page`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val committee = committeeFactory.create(name = "Board Edit Committee ${System.currentTimeMillis()}")
        val event = createFutureEvent(
            committee = committee,
            approved = false,
            title = "Board Edit Approval ${System.currentTimeMillis()}"
        )
        val eventId = checkNotNull(event.id) { "Expected event id" }

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            EventFormHelper.openEditPage(page, frontendUrl, eventId)
            EventFormHelper.setApproved(page, approved = true)

            val response = page.waitForResponse(
                Predicate { r -> r.request().method() == "PUT" && r.url().contains("/events/$eventId") }
            ) {
                EventFormHelper.submit(page)
            }
            assertThat(response.status()).isEqualTo(200)
        }

        assertThat(waitForEventById(eventId).approved).isTrue()
    }

    @Test
    fun `events page fetches banner after editing event banner`() {
        val member = userFactory.createUserWithRole(Role.COMMITTEE, enabled = true)
        val committee = committeeFactory.create(name = "Banner Edit Committee ${System.currentTimeMillis()}")
        committeeFactory.createMember(committee, member)
        val event = createFutureEvent(
            committee = committee,
            approved = true,
            title = "Edit Banner Event ${System.currentTimeMillis()}"
        )
        val eventId = checkNotNull(event.id) { "Expected event id" }

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            EventFormHelper.openEditPage(page, frontendUrl, eventId)
            EventFormHelper.uploadBanner(page, EVENT_BANNER_PATH)

            val updateResponse = page.waitForResponse(
                Predicate { r -> r.request().method() == "PUT" && r.url().contains("/events/$eventId") }
            ) {
                EventFormHelper.submit(page)
            }
            assertThat(updateResponse.status()).isEqualTo(200)
        }

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            val bannerResponse = page.waitForResponse(
                Predicate { r -> r.request().method() == "GET" && r.url().contains("/events/$eventId/banners") }
            ) {
                page.navigate("$frontendUrl/events")
            }
            assertThat(bannerResponse.status()).isEqualTo(200)
        }
    }

    private fun createFutureEvent(
        committee: net.blueshell.api.domain.committee.persistence.Committee,
        approved: Boolean,
        title: String
    ): Event {
        val event = eventFactory.create(
            committee = committee,
            approved = approved,
            signUp = false,
            title = title
        )
        event.startTime = Instant.now().plusSeconds(7 * 24 * 3600)
        event.endTime = Instant.now().plusSeconds(7 * 24 * 3600 + 3600)
        return eventRepository.saveAndFlush(event)
    }

    private fun waitForEventById(eventId: Long): Event {
        lateinit var event: Event
        waitFor(
            onTimeoutMessage = { "Expected event $eventId to exist" }
        ) {
            val found = eventRepository.findById(eventId)
            if (found.isPresent) {
                event = found.get()
                true
            } else {
                false
            }
        }
        return event
    }

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
        const val EVENT_BANNER_PATH = "../frontend/public/favicon.png"
    }
}
