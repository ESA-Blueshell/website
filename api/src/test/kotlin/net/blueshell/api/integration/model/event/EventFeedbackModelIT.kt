package net.blueshell.api.integration.model.event

import net.blueshell.api.integration.model.ModelPersistenceTestSupport
import net.blueshell.api.model.event.EventFeedback
import net.blueshell.api.model.event.Event
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EventFeedbackModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists column fields`() {
            val event = persistEvent()
            val feedback = eventFeedbackFactory.createBasic()
            feedback.event = event
            feedback.feedback = "Feedback"

            val found = persistAndReload(feedback, EventFeedback::class.java) { it.id }

            assertEquals("Feedback", found.feedback)
        }

        @Test
        fun `persists event relation when setting entity`() {
            val event = persistEvent()
            val feedback = eventFeedbackFactory.createBasic()
            feedback.event = event
            feedback.feedback = "Feedback"

            val found = persistAndReload(feedback, EventFeedback::class.java) { it.id }

            assertEquals(event.id, found.event.id)
        }

        @Test
        fun `persists event relation when setting id`() {
            val event = persistEvent()
            val feedback = eventFeedbackFactory.createBasic()
            feedback.event = entityManager.getReference(Event::class.java, event.id)
            feedback.feedback = "Feedback"

            val found = persistAndReload(feedback, EventFeedback::class.java) { it.id }

            assertEquals(event.id, found.event.id)
        }
    }
}
