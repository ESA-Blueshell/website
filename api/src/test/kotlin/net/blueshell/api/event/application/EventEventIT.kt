package net.blueshell.api.event.application

import net.blueshell.api.event.application.event.EventChangedEvent
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.factory.model.committee.CommitteeFactory
import net.blueshell.api.factory.model.event.EventFactory
import net.blueshell.api.factory.model.event.GuestFactory
import net.blueshell.api.platform.integration.event.job.AddEventToCalendarEvent
import net.blueshell.api.platform.integration.event.job.EventSignupEmailEvent
import net.blueshell.api.platform.integration.event.job.RemoveEventFromCalendarEvent
import net.blueshell.api.platform.integration.event.job.SyncEventToCalendarEvent
import net.blueshell.api.testsupport.EventIntegrationTestSupport
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class EventEventIT : EventIntegrationTestSupport() {

    @Autowired
    private lateinit var events: EventService

    @Autowired
    private lateinit var signUps: EventSignUpService

    @Autowired
    private lateinit var committeeFactory: CommitteeFactory

    @Autowired
    private lateinit var eventFactory: EventFactory

    @Autowired
    private lateinit var guestFactory: GuestFactory

    @Test
    fun `publishes add event when approved event is created`() {
        val committee = persist(committeeFactory.createBasic())
        val event = eventFactory.createWithCustomizations {
            it.committee = committee
            it.approved = true
        }

        val saved = events.create(event)

        assertTrue(applicationEvents.stream(EventChangedEvent::class.java).anyMatch { it.eventId == saved.id })
        assertTrue(applicationEvents.stream(AddEventToCalendarEvent::class.java).anyMatch { it.eventId == saved.id })
    }

    @Test
    fun `publishes sync event when approved event is updated`() {
        val committee = persist(committeeFactory.createBasic())
        val event = eventFactory.createWithCustomizations {
            it.committee = committee
            it.approved = true
        }

        val saved = events.create(event)
        saved.title = saved.title + " updated"
        saved.approved = true
        events.update(saved)

        assertTrue(applicationEvents.stream(SyncEventToCalendarEvent::class.java).anyMatch { it.eventId == saved.id })
    }

    @Test
    fun `publishes remove event when event is deleted`() {
        val committee = persist(committeeFactory.createBasic())
        val event = eventFactory.createWithCustomizations {
            it.committee = committee
            it.approved = true
        }

        val saved = events.create(event)
        events.delete(saved)

        assertTrue(applicationEvents.stream(RemoveEventFromCalendarEvent::class.java).anyMatch { it.eventId == saved.id })
    }

    @Test
    fun `publishes signup email event for guest signups`() {
        val committee = persist(committeeFactory.createBasic())
        val event = events.create(
            eventFactory.createWithCustomizations {
                it.committee = committee
                it.approved = true
                it.signUp = true
            }
        )
        val guest = persist(guestFactory.createBasic())

        val signUp = EventSignUp()
        signUp.event = event
        signUp.guest = guest

        val saved = signUps.create(signUp)

        assertTrue(
            applicationEvents.stream(EventSignupEmailEvent::class.java)
                .anyMatch { it.eventSignUpId == saved.id }
        )
    }
}
