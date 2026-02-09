package net.blueshell.api.event.application

import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.factory.model.committee.CommitteeFactory
import net.blueshell.api.factory.model.event.EventFactory
import net.blueshell.api.factory.model.event.GuestFactory
import net.blueshell.api.platform.integration.calendar.job.AddEventToCalendarJobHandler
import net.blueshell.api.platform.integration.calendar.job.RemoveEventFromCalendarJobHandler
import net.blueshell.api.platform.integration.calendar.job.SyncEventToCalendarJobHandler
import net.blueshell.api.platform.integration.email.job.EventSignupEmailJobHandler
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

        assertTrue(jobExecutions.findByJobType(AddEventToCalendarJobHandler.JOB_TYPE).isNotEmpty())
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

        assertTrue(jobExecutions.findByJobType(SyncEventToCalendarJobHandler.JOB_TYPE).isNotEmpty())
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

        assertTrue(jobExecutions.findByJobType(RemoveEventFromCalendarJobHandler.JOB_TYPE).isNotEmpty())
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
            jobExecutions.findByJobType(EventSignupEmailJobHandler.JOB_TYPE).isNotEmpty()
        )
    }
}
