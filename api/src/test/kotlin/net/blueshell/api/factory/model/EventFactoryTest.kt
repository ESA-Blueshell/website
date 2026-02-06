package net.blueshell.api.factory.model

import org.junit.jupiter.api.Test

class EventFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable event`() {
        val committee = persistCommittee()
        val event = eventFactory.createBasic()
        event.committee = committee
        event.committeeId = committee.id

        val saved = persist(event)
        assertPersisted(net.blueshell.api.model.event.Event::class.java, saved.id)
    }
}
