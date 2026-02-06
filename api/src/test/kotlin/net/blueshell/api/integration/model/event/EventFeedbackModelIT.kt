package net.blueshell.api.integration.model.event

import net.blueshell.api.integration.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EventFeedbackModelIT : net.blueshell.api.integration.model.ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_columns_and_event_relation() {
            val event = persistEvent()
            val feedback = eventFeedbackFactory.createBasic()
            feedback.event = event
            feedback.feedback = "Feedback"

            val found = persistAndReload(feedback, EventFeedback::class.java) { it.id }

            assertEquals("Feedback", found.feedback)
            assertEquals(event.id, found.event.id)
        }
    }
}
