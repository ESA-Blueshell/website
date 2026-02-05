package net.blueshell.api.model.event

import jakarta.persistence.*
import net.blueshell.api.model.base.AuditedSoftDeleteEntity
import net.blueshell.api.model.base.Identifiable
import net.blueshell.api.model.File
import org.hibernate.Hibernate
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "event_pictures",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_event_pictures_event_picture_deleted_at",
            columnNames = ["event_id", "picture_id", "deleted_at"]
        ),
        UniqueConstraint(name = "uk_event_pictures_picture_deleted_at", columnNames = ["picture_id", "deleted_at"])
    ],
    indexes = [
        Index(name = "idx_event_pictures_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_event_pictures_event_id", columnList = "event_id"),
        Index(name = "idx_event_pictures_picture_id", columnList = "picture_id")
    ]
)
@SQLDelete(
    sql = """
      UPDATE event_pictures
      SET deleted_at = NOW(), version = version + 1
      WHERE event_id = ? AND picture_id = ? AND version = ?
    """
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
open class EventPicture(
    @EmbeddedId
    override var id: EventPictureId = EventPictureId()
) : AuditedSoftDeleteEntity(), Identifiable<EventPictureId> {

    @get:Transient
    @set:Transient
    var eventId: Long
        get() = requireNotNull(id.eventId) { "eventId is required" }
        set(value) {
            id.eventId = value
        }

    @get:Transient
    @set:Transient
    var pictureId: Long
        get() = requireNotNull(id.pictureId) { "pictureId is required" }
        set(value) {
            id.pictureId = value
        }

    @field:MapsId("pictureId")
    @field:OneToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "picture_id", nullable = false)
    private var _picture: File? = null
    var picture: File
        get() = requireNotNull(_picture) { "Picture is required" }
        set(value) {
            _picture = value
            value.id?.let { pictureId = it }
        }

    @field:MapsId("eventId")
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "event_id", nullable = false)
    private var _event: Event? = null
    var event: Event
        get() = requireNotNull(_event) { "Event is required" }
        set(value) {
            _event = value
            value.id?.let { eventId = it }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as EventPicture
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

@Embeddable
data class EventPictureId(
    var eventId: Long? = null,
    var pictureId: Long? = null
) : java.io.Serializable
