package net.blueshell.api.model.event

import jakarta.persistence.*
import net.blueshell.api.base.entity.AuditedAutoIdEntity
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
class EventFeedback : AuditedAutoIdEntity() {
    @Column(name = "feedback", nullable = false)
    lateinit var feedback: String

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "event_id", nullable = false)
    private var _event: Event? = null
    var event: Event
        get() = requireNotNull(_event) { "Event is required" }
        set(value) {
            _event = value
        }
}
