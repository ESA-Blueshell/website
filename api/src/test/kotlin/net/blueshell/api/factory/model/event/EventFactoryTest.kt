package net.blueshell.api.factory.model.event

import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.factory.model.ModelFactoryTestSupport
import org.junit.jupiter.api.Test

class EventFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable event`() {
        val committee = persistCommittee()
        val event = eventFactory.createBasic()
        event.committee = committee

        val saved = persist(event)
        assertPersisted(Event::class.java, saved.id)
    }

    @Test
    fun `creates persistable approved event`() {
        val committee = persistCommittee()
        val event = eventFactory.createApproved()
        event.committee = committee

        val saved = persist(event)
        assertPersisted(Event::class.java, saved.id)
    }

    @Test
    fun `creates persistable event with signup form`() {
        val committee = persistCommittee()
        val event = eventFactory.createWithSignUp()
        event.committee = committee

        val saved = persist(event)
        assertPersisted(Event::class.java, saved.id)
    }
}
