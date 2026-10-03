package net.blueshell.api.event.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import net.blueshell.api.shared.model.Identifiable
import org.springframework.data.jpa.repository.JpaRepository

/** An event as the board last approved it, field by field, so a re-approval can say what changed. */
@Entity
@Table(name = "approved_event")
class ApprovedEvent(
    @Id
    @Column(name = "event_id", nullable = false)
    val eventId: Long,
    /** Each compared field's value when approved, as a JSON object keyed by field. */
    @Lob
    @Column(name = "fields", nullable = false)
    var fields: String,
) : Identifiable<Long> {
    override val id: Long get() = eventId
}

interface ApprovedEventRepository : JpaRepository<ApprovedEvent, Long>
