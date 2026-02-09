package net.blueshell.api.event.application

import net.blueshell.api.factory.model.committee.CommitteeFactory
import net.blueshell.api.factory.model.event.EventFactory
import net.blueshell.api.platform.integration.calendar.job.AddEventToCalendarJob
import net.blueshell.api.platform.integration.calendar.job.RemoveEventFromCalendarJob
import net.blueshell.api.platform.integration.calendar.job.SyncEventToCalendarJob
import net.blueshell.api.testsupport.EventIntegrationTestSupport
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class EventServiceIT : EventIntegrationTestSupport() {

    @Autowired
    private lateinit var events: EventService

    @Autowired
    private lateinit var committeeFactory: CommitteeFactory

    @Autowired
    private lateinit var eventFactory: EventFactory

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

            assertTrue(jobExecutions.findByJobType(AddEventToCalendarJob.TYPE).isNotEmpty())
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
            saved.title = saved.title + " updated"
            saved.approved = true
            events.update(saved)

            assertTrue(jobExecutions.findByJobType(SyncEventToCalendarJob.TYPE).isNotEmpty())
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

            assertTrue(jobExecutions.findByJobType(RemoveEventFromCalendarJob.TYPE).isNotEmpty())
        }
    }
}
