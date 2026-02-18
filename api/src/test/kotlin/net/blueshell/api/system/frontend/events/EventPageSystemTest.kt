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

    @Test
    fun `calendar toolbar navigation works`() {
        withPage { page ->
            page.setViewportSize(1280, 900)
            EventPageHelper.open(page, frontendUrl)

            val initialMonth = EventPageHelper.monthTitle(page)
            EventPageHelper.goNextMonth(page)
            waitFor(
                onTimeoutMessage = { "Expected calendar month title to change after moving to next month" }
            ) {
                EventPageHelper.monthTitle(page) != initialMonth
            }

            EventPageHelper.goToday(page)
            waitFor(
                onTimeoutMessage = { "Expected calendar month title to return to current month after clicking Today" }
            ) {
                EventPageHelper.monthTitle(page) == initialMonth
            }

            EventPageHelper.goPrevMonth(page)
            waitFor(
                onTimeoutMessage = { "Expected calendar month title to change after moving to previous month" }
            ) {
                EventPageHelper.monthTitle(page) != initialMonth
            }
        }
    }

    @Test
    fun `calendar event click opens event details`() {
        val committee = committeeFactory.create(name = "Calendar Committee ${System.currentTimeMillis()}")
        val event = createCurrentMonthEvent(
            committee = committee,
            approved = true,
            signUp = false,
            title = "Calendar Detail Event ${System.currentTimeMillis()}"
        )

        withPage { page ->
            EventPageHelper.open(page, frontendUrl)
            EventPageHelper.waitForEventVisible(page, event.title)
            EventPageHelper.openCalendarEvent(page, event.title)

            waitFor(
                onTimeoutMessage = { "Expected event details popup for ${event.title}" }
            ) {
                page.getByText("When", Page.GetByTextOptions().setExact(true)).count() > 0
            }
        }
    }

    @Test
    fun `event card supports adding to calendar and copying link`() {
        val committee = committeeFactory.create(name = "Share Committee ${System.currentTimeMillis()}")
        val event = createCurrentMonthEvent(
            committee = committee,
            approved = true,
            signUp = false,
            title = "Share Event ${System.currentTimeMillis()}"
        )
        val eventId = checkNotNull(event.id) { "Expected event id" }

        withPage { page ->
            EventPageHelper.open(page, frontendUrl)
            EventPageHelper.waitForEventVisible(page, event.title)

            val download = page.waitForDownload {
                EventPageHelper.clickCardIcon(page, event.title, "mdi-calendar")
            }
            assertThat(download.suggestedFilename()).endsWith(".ics")

            page.evaluate(
                """
                () => {
                  window.__copied = "";
                  Object.defineProperty(navigator, "clipboard", {
                    configurable: true,
                    value: { writeText: (value) => { window.__copied = value; return Promise.resolve(); } }
                  });
                }
                """.trimIndent()
            )

            EventPageHelper.clickCardIcon(page, event.title, "mdi-share-variant")
            waitFor(
                onTimeoutMessage = { "Expected share link to be written to clipboard for event ${event.title}" }
            ) {
                val copied = page.evaluate("() => window.__copied")?.toString().orEmpty()
                copied.isNotBlank()
            }

            val copied = page.evaluate("() => window.__copied")?.toString().orEmpty()
            assertThat(copied).isEqualTo("${page.url().substringBefore("?")}#$eventId")
        }
    }

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
                Predicate { r ->
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
    fun `committee member can open signups and edit pages from event card`() {
        val member = userFactory.createUserWithRole(Role.COMMITTEE, enabled = true)
        val committee = committeeFactory.create(name = "Card Navigation Committee ${System.currentTimeMillis()}")
        committeeFactory.createMember(committee, member)
        val event = createCurrentMonthEvent(
            committee = committee,
            approved = true,
            signUp = true,
            title = "Card Navigation Event ${System.currentTimeMillis()}"
        )
        val eventId = checkNotNull(event.id) { "Expected event id" }

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            EventPageHelper.open(page, frontendUrl)
            EventPageHelper.waitForEventVisible(page, event.title)

            EventPageHelper.clickCardIcon(page, event.title, "mdi-list-status")
            page.waitForURL("**/events/signups/$eventId**")

            page.navigate("$frontendUrl/events")
            EventPageHelper.waitForEventVisible(page, event.title)

            EventPageHelper.clickCardIcon(page, event.title, "mdi-pencil")
            page.waitForURL("**/events/edit/$eventId**")
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
                Predicate { r -> r.request().method() == "DELETE" && r.url().contains("/events/$eventId") }
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

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
