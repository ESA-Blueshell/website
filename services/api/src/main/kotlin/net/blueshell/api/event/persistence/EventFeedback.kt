package net.blueshell.api.event.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "event_feedback",
    indexes = [
        Index(name = "idx_event_feedback_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_event_feedback_event_id", columnList = "event_id")
    ]
)
@SQLDelete(sql = "UPDATE event_feedback SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class EventFeedback(
    @Column(name = "feedback", nullable = false)
    var feedback: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    var event: Event,
) : AuditedAutoIdEntity() {
    val eventId: Long
        get() = event.id ?: 0
}
