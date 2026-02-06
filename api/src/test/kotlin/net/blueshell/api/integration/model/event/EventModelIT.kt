package net.blueshell.api.integration.model.event

import net.blueshell.api.integration.model.ModelPersistenceTestSupport
import net.blueshell.api.model.event.Event
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EventModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_columns_and_committee_relation() {
            val committee = persistCommittee()
            val event = eventFactory.createBasic()
            event.committee = committee
            event.committeeId = committee.id
            event.title = unique("event")
            event.description = "Event description"
            event.location = "HQ"
            event.startTime = timestamp().plusSeconds(3600)
            event.endTime = timestamp().plusSeconds(7200)
            event.memberPrice = 12.5
            event.publicPrice = 25.0
            event.googleId = unique("google")
            event.approved = true
            event.membersOnly = true
            event.signUp = false

            val found = persistAndReload(event, Event::class.java) { it.id }

            assertEquals(committee.id, found.committeeId)
            assertEquals(event.title, found.title)
            assertEquals(event.description, found.description)
            assertEquals(event.location, found.location)
            assertEquals(event.startTime, found.startTime)
            assertEquals(event.endTime, found.endTime)
            assertEquals(event.memberPrice, found.memberPrice)
            assertEquals(event.publicPrice, found.publicPrice)
            assertEquals(event.googleId, found.googleId)
            assertEquals(event.approved, found.approved)
            assertEquals(event.membersOnly, found.membersOnly)
            assertEquals(event.signUp, found.signUp)
        }
    }
}
