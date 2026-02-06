package net.blueshell.api.factory.model

import org.junit.jupiter.api.Test

class EventFeedbackFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable event feedback`() {
        val event = persistEvent()
        val feedback = eventFeedbackFactory.createBasic()
        feedback.event = event

        val saved = persist(feedback)
        assertPersisted(net.blueshell.api.model.event.EventFeedback::class.java, saved.id)
    }
}
