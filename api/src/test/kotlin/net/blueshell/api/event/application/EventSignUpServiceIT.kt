package net.blueshell.api.event.application

import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.factory.model.committee.CommitteeFactory
import net.blueshell.api.factory.model.event.EventFactory
import net.blueshell.api.factory.model.event.GuestFactory
import net.blueshell.api.platform.integration.email.job.EventSignupEmailJob
import net.blueshell.api.platform.integration.queue.EmailJobs
import net.blueshell.api.testsupport.EventIntegrationTestSupport
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class EventSignUpServiceIT : EventIntegrationTestSupport() {

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

    @Nested
    inner class Create {

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

            signUps.create(signUp)

            assertTrue(
                jobExecutions.findByJobType(EmailJobs.EventSignup.type).isNotEmpty()
            )
        }
    }
}
