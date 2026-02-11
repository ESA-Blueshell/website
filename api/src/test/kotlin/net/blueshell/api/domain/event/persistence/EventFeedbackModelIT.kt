package net.blueshell.api.domain.event.persistence

import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions
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

            assertEquals(event.id, found.eventId)
            Assertions.assertEquals(event.id, found.event.id)
        }
    }
}
