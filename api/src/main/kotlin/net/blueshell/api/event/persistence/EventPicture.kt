package net.blueshell.api.event.persistence

import jakarta.persistence.*
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.model.AuditedSoftDeleteEntity
import net.blueshell.api.shared.model.Identifiable
import net.blueshell.api.shared.model.asRef
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
class EventPicture(
    @EmbeddedId
    override var id: Id = Id()
) : AuditedSoftDeleteEntity(), Identifiable<EventPicture.Id> {

    val eventId: Long
        get() = id.eventId ?: 0

    val pictureId: Long
        get() = id.pictureId ?: 0

    @MapsId("pictureId")
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "picture_id", nullable = false)
    lateinit var picture: File
        internal set

    @MapsId("eventId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    lateinit var event: Event
        internal set

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as EventPicture
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()


    @Embeddable
    data class Id(
        var eventId: Long? = null,
        var pictureId: Long? = null
    ) : java.io.Serializable

}
