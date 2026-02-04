package net.blueshell.api.model.event

import jakarta.persistence.*
import net.blueshell.api.base.entity.AuditedAutoIdEntity
import net.blueshell.api.model.File
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "event_pictures",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_event_pictures_event_picture_deleted_at",
        columnNames = ["event_id", "picture_id", "deleted_at"]
    ), UniqueConstraint(name = "uk_event_pictures_picture_deleted_at", columnNames = ["picture_id", "deleted_at"])],
    indexes = [Index(
        name = "idx_event_pictures_deleted_at",
        columnList = "deleted_at"
    ), Index(
        name = "idx_event_pictures_event_id",
        columnList = "event_id"
    ), Index(name = "idx_event_pictures_picture_id", columnList = "picture_id")]
)
@SQLDelete(sql = "UPDATE event_pictures SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class EventPicture : AuditedAutoIdEntity() {
    @field:OneToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "picture_id", nullable = false)
    private var _picture: File? = null
    var picture: File
        get() = requireNotNull(_picture) { "Picture is required" }
        set(value) {
            _picture = value
        }

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "event_id", nullable = false)
    private var _event: Event? = null
    var event: Event
        get() = requireNotNull(_event) { "Event is required" }
        set(value) {
            _event = value
        }
}
