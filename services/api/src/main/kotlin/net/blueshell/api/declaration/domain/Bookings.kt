package net.blueshell.api.declaration.domain

import net.blueshell.api.committee.api.CommitteeNotFound
import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.declaration.persistence.Booking
import net.blueshell.api.declaration.persistence.BookingKind
import net.blueshell.api.event.api.EventService
import org.springframework.stereotype.Component

/** A committee or event an expense is for, as the submitter picked it. */
data class BookingChoice(
    val kind: BookingKind,
    val id: Long,
)

/** Names the committee or event an expense is for, refusing one that does not exist. */
@Component
class Bookings(
    private val committees: CommitteeService,
    private val events: EventService,
) {
    fun resolve(choice: BookingChoice): Booking =
        when (choice.kind) {
            BookingKind.COMMITTEE ->
                try {
                    Booking(choice.kind, choice.id, committees.findById(choice.id).name)
                } catch (_: CommitteeNotFound) {
                    throw UnknownBooking()
                }
            BookingKind.EVENT ->
                events
                    .findByIdIncludingDeletedOrNull(choice.id)
                    // An unapproved event is its committee's and the board's; its title is not the submitter's to learn.
                    ?.takeIf { it.approved && !it.isSoftDeleted }
                    ?.let { Booking(choice.kind, choice.id, it.title) }
                    ?: throw UnknownBooking()
        }
}
