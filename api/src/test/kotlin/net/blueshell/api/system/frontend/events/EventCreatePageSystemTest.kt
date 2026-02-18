package net.blueshell.api.system.frontend.events

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
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
class EventCreatePageSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var userFactory: UserFactory

    @Autowired
    private lateinit var committeeFactory: CommitteeFactory

    @Autowired
    private lateinit var eventFactory: EventFactory

    @Autowired
    private lateinit var eventRepository: EventRepository

    @Test
    fun `committee member can only select own committees on event create`() {
        val member = userFactory.createUserWithRole(Role.COMMITTEE, enabled = true)
        val ownCommittee = committeeFactory.create(name = "Own Committee ${System.currentTimeMillis()}")
        val otherCommittee = committeeFactory.create(name = "Other Committee ${System.currentTimeMillis()}")
        committeeFactory.createMember(ownCommittee, member)

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            EventFormHelper.openCreatePage(page, frontendUrl)
            EventFormHelper.openCommitteeSelect(page)

            assertThat(
                page.getByText(ownCommittee.name, Page.GetByTextOptions().setExact(true)).count()
            ).isGreaterThan(0)

            assertThat(
                page.getByText(otherCommittee.name, Page.GetByTextOptions().setExact(true)).count()
            ).isEqualTo(0)
        }
    }

    @Test
    fun `committee member created events stay unapproved`() {
        val member = userFactory.createUserWithRole(Role.COMMITTEE, enabled = true)
        val committee = committeeFactory.create(name = "Member Committee ${System.currentTimeMillis()}")
        committeeFactory.createMember(committee, member)
        val eventTitle = "Committee Event ${System.currentTimeMillis()}"

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            EventFormHelper.openCreatePage(page, frontendUrl)
            EventFormHelper.fillRequiredFields(
                page = page,
                title = eventTitle,
                location = "Campus",
                description = "Committee created event"
            )
            EventFormHelper.selectCommittee(page, committee.name)

            val response = page.waitForResponse(
                Predicate { r ->
                    r.request().method() == "POST" &&
                        r.url().contains("/events") &&
                        !r.url().contains("/events/banners")
                }
            ) {
                EventFormHelper.submit(page)
            }
            assertThat(response.status()).isEqualTo(201)
        }

        val created = waitForEventByTitle(eventTitle)
        assertThat(created.approved).isFalse()
        assertThat(created.committeeId).isEqualTo(committee.id)
    }

    @Test
    fun `board can create approved event for any committee`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val committeeA = committeeFactory.create(name = "A Committee ${System.currentTimeMillis()}")
        val committeeB = committeeFactory.create(name = "B Committee ${System.currentTimeMillis()}")
        val eventTitle = "Board Created Event ${System.currentTimeMillis()}"

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            EventFormHelper.openCreatePage(page, frontendUrl)
            EventFormHelper.fillRequiredFields(
                page = page,
                title = eventTitle,
                location = "Meeting Room",
                description = "Board created event"
            )
            EventFormHelper.selectCommittee(page, committeeB.name)
            EventFormHelper.setApproved(page, approved = true)

            val response = page.waitForResponse(
                Predicate { r ->
                    r.request().method() == "POST" &&
                        r.url().contains("/events") &&
                        !r.url().contains("/events/banners")
                }
            ) {
                EventFormHelper.submit(page)
            }
            assertThat(response.status()).isEqualTo(201)
        }

        val created = waitForEventByTitle(eventTitle)
        assertThat(created.approved).isTrue()
        assertThat(created.committeeId).isEqualTo(committeeB.id)
        assertThat(created.committeeId).isNotEqualTo(committeeA.id)
    }

    @Test
    fun `events page fetches banner for newly created event`() {
        val member = userFactory.createUserWithRole(Role.COMMITTEE, enabled = true)
        val committee = committeeFactory.create(name = "Banner Committee ${System.currentTimeMillis()}")
        committeeFactory.createMember(committee, member)
        val eventTitle = "Banner Event ${System.currentTimeMillis()}"

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            EventFormHelper.openCreatePage(page, frontendUrl)
            EventFormHelper.fillRequiredFields(
                page = page,
                title = eventTitle,
                location = "Campus",
                description = "Event with banner upload"
            )
            EventFormHelper.selectCommittee(page, committee.name)
            EventFormHelper.uploadBanner(page, EVENT_BANNER_PATH)

            val createResponse = page.waitForResponse(
                Predicate { r ->
                    r.request().method() == "POST" &&
                        r.url().contains("/events") &&
                        !r.url().contains("/events/banners")
                }
            ) {
                EventFormHelper.submit(page)
            }
            assertThat(createResponse.status()).isEqualTo(201)
        }

        val created = waitForEventByTitle(eventTitle)
        val eventId = checkNotNull(created.id) { "Expected created event id for title '$eventTitle'" }

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            val bannerResponse = page.waitForResponse(
                Predicate { r ->
                    r.request().method() == "GET" &&
                        r.url().contains("/events/$eventId/banners")
                }
            ) {
                page.navigate("$frontendUrl/events")
            }
            assertThat(bannerResponse.status()).isEqualTo(200)
        }
    }

    @Test
    fun `board can approve event from events page`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val committee = committeeFactory.create(name = "Approve Committee ${System.currentTimeMillis()}")
        committeeFactory.createMember(committee, board)
        val event = eventFactory.create(
            committee = committee,
            approved = false,
            signUp = false,
            title = "Approve From Events Page ${System.currentTimeMillis()}"
        )
        event.startTime = Instant.now().plusSeconds(7 * 24 * 3600)
        event.endTime = Instant.now().plusSeconds(7 * 24 * 3600 + 3600)
        eventRepository.saveAndFlush(event)
        val eventId = checkNotNull(event.id) { "Expected event id" }

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            page.navigate("$frontendUrl/events")
            waitFor(
                timeoutMs = 12_000,
                onTimeoutMessage = { "Expected event '${event.title}' to be visible on events page for board user" }
            ) {
                page.getByText(event.title, Page.GetByTextOptions().setExact(false)).count() > 0
            }

            val response = page.waitForResponse(
                Predicate { r ->
                    r.request().method() == "PUT" &&
                        r.url().contains("/events/$eventId/approve") &&
                        r.url().contains("approved=true")
                }
            ) {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Awaiting approval").setExact(false)
                ).first().click()
            }
            assertThat(response.status()).isEqualTo(200)
        }

        waitFor(
            onTimeoutMessage = { "Expected event $eventId to be approved by board action" }
        ) {
            eventRepository.findById(eventId).orElseThrow().approved
        }
    }

    private fun waitForEventByTitle(title: String): Event {
        lateinit var created: Event
        waitFor(
            onTimeoutMessage = { "Expected persisted event with title '$title'" }
        ) {
            val found = eventRepository.findAll().firstOrNull { it.title == title }
            if (found != null) {
                created = found
                true
            } else {
                false
            }
        }
        return created
    }

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
        const val EVENT_BANNER_PATH = "../frontend/public/favicon.png"
    }
}
