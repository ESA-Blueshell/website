package net.blueshell.api.event.application

 import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.factory.model.committee.CommitteeFactory
import net.blueshell.api.factory.model.event.EventFactory
import net.blueshell.api.factory.model.event.GuestFactory
import net.blueshell.api.platform.integration.queue.CalendarJobs
import net.blueshell.api.testsupport.ServiceTestSupport
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class EventServiceIT : ServiceTestSupport() {

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

    @Autowired
    private lateinit var userFactory: UserFactory

    @Nested
    inner class Create {

        @Test
        fun `publishes add event when approved event is created`() {
            val committee = persist(committeeFactory.createBasic())
            val event = eventFactory.createWithCustomizations {
                it.committee = committee
                it.approved = true
            }

            events.create(event)

            assertTrue(jobExecutions.findByJobType(CalendarJobs.AddEvent.type).isNotEmpty())
        }
    }

    @Nested
    inner class Update {

        @Test
        fun `publishes sync event when approved event is updated`() {
            val committee = persist(committeeFactory.createBasic())
            val event = eventFactory.createWithCustomizations {
                it.committee = committee
                it.approved = true
            }

            val saved = events.create(event)
            saved.title += " updated"
            saved.approved = true
            events.update(saved)

            assertTrue(jobExecutions.findByJobType(CalendarJobs.SyncEvent.type).isNotEmpty())
        }
    }

    @Nested
    inner class Delete {

        @Test
        fun `publishes remove event when event is deleted`() {
            val committee = persist(committeeFactory.createBasic())
            val event = eventFactory.createWithCustomizations {
                it.committee = committee
                it.approved = true
            }

            val saved = events.create(event)
            events.delete(saved)

            assertTrue(jobExecutions.findByJobType(CalendarJobs.RemoveEvent.type).isNotEmpty())
        }

        @Test
        fun `deletes signups when event is deleted`() {
            val committee = persist(committeeFactory.createBasic())
            val event = events.create(
                eventFactory.createWithCustomizations {
                    it.committee = committee
                    it.approved = true
                    it.signUp = true
                }
            )
            val guest = persist(guestFactory.createBasic())
            val user = persist(userFactory.createBasic())

            val guestSignUp = EventSignUp()
            guestSignUp.event = event
            guestSignUp.guest = guest
            signUps.create(guestSignUp)

            val userSignUp = EventSignUp()
            userSignUp.event = event
            userSignUp.user = user
            signUps.create(userSignUp)

            events.delete(event)

            assertTrue(signUps.findByEventId(event.id!!).isEmpty())
        }
    }
}
