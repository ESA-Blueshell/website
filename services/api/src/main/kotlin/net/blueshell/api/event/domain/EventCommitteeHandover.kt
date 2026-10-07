package net.blueshell.api.event.domain

import net.blueshell.api.committee.api.CommitteeEvents
import net.blueshell.api.event.persistence.EventRepository
import org.springframework.stereotype.Component

/** The events a committee organises, counted and handed to another committee when it is deleted. */
@Component
class EventCommitteeHandover(
    private val events: EventRepository,
) : CommitteeEvents {
    override fun countOf(committeeId: Long): Long = events.countOrganisedBy(committeeId)

    override fun handOver(
        from: Long,
        to: Long,
    ) {
        events.handOverCommittee(from, to)
    }
}
