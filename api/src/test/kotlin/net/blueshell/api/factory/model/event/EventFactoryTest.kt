package net.blueshell.api.factory.model.event

import net.blueshell.api.factory.model.ModelFactoryTestSupport
import net.blueshell.api.model.event.Event
import org.junit.jupiter.api.Test

class EventFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable event`() {
        val committee = persistCommittee()
        val event = eventFactory.createBasic()
        event.committee = committee
        event.committeeId = committee.id

        val saved = persist(event)
        assertPersisted(Event::class.java, saved.id)
    }

    @Test
    fun `creates persistable approved event`() {
        val committee = persistCommittee()
        val event = eventFactory.createApproved()
        event.committee = committee
        event.committeeId = committee.id

        val saved = persist(event)
        assertPersisted(Event::class.java, saved.id)
    }

    @Test
    fun `creates persistable event with signup form`() {
        val committee = persistCommittee()
        val event = eventFactory.createWithSignUp()
        event.committee = committee
        event.committeeId = committee.id

        val saved = persist(event)
        assertPersisted(Event::class.java, saved.id)
    }
}
