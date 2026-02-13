package net.blueshell.api.domain.event.application

import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.factory.model.committee.CommitteeFactory
import net.blueshell.api.factory.model.event.EventFactory
import net.blueshell.api.factory.model.event.GuestFactory
import net.blueshell.api.platform.integration.queue.EmailJobs
import net.blueshell.api.testsupport.ServiceTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class EventSignUpServiceIT : ServiceTestSupport() {

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
        fun `can create signup for guest`() {
            val committee = persist(committeeFactory.createBasic())
            val event = events.create(
                eventFactory.createWithCustomizations {
                    it.committee = committee
                    it.approved = true
                    it.signUp = true
                }
            )

            val signUp = EventSignUp()
            signUp.event = event
            signUp.guest = guestFactory.createBasic()

            signUps.create(signUp)

            assertEquals(1, signUps.findByEventId(event.id!!).size)
        }

        @Test
        fun `can create signup for user`() {
            val committee = persist(committeeFactory.createBasic())
            val event = events.create(
                eventFactory.createWithCustomizations {
                    it.committee = committee
                    it.approved = true
                    it.signUp = true
                }
            )
            val user = persist(userFactory.createBasic())

            val signUp = EventSignUp()
            signUp.event = event
            signUp.userId = user.id

            signUps.create(signUp)

            assertEquals(1, signUps.findByEventId(event.id!!).size)
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

            val signUp = EventSignUp()
            signUp.event = event
            signUp.guest = guestFactory.createBasic()

            signUps.create(signUp)

            assertTrue(
                jobExecutions.findByJobType(EmailJobs.EventSignup.type).isNotEmpty()
            )
        }
    }
}
