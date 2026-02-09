package net.blueshell.api.factory.model.event

import net.blueshell.api.factory.model.ModelFactoryTestSupport
import net.blueshell.api.event.model.EventFeedback
import org.junit.jupiter.api.Test

class EventFeedbackFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable event feedback`() {
        val event = persistEvent()
        val feedback = eventFeedbackFactory.createBasic()
        feedback.event = event

        val saved = persist(feedback)
        assertPersisted(EventFeedback::class.java, saved.id)
    }
}
