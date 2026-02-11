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
    name = "event_banners",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_event_file", columnNames = ["event_id", "file_id", "deleted_at"])
    ],
    indexes = [
        Index(name = "idx_event_banners_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_event_banners_event", columnList = "event_id"),
        Index(name = "idx_event_banners_file", columnList = "file_id")
    ]
)
@SQLDelete(
    sql = """
      UPDATE event_banners
      SET deleted_at = NOW(), version = version + 1
      WHERE event_id = ? AND file_id = ? AND version = ?
    """
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class EventBanner(
    @EmbeddedId
    override var id: Id = Id()
) : AuditedSoftDeleteEntity(), Identifiable<EventBanner.Id> {

    @field:MapsId("eventId")
    @field:OneToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "event_id", nullable = false)
    private var _event: Event? = null
    var event: Event
        get() = requireNotNull(_event) { "Event is required" }
        set(value) {
            _event = value
            id.eventId = value.id
        }

    val eventId: Long get() = requireNotNull(id.eventId) { "eventId is required" }

    @field:MapsId("fileId")
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(
        name = "file_id",
        nullable = false,
        foreignKey = ForeignKey(name = "fk_event_banners_file")
    )
    private var _file: File? = null
    var file: File
        get() = requireNotNull(_file) { "File is required" }
        set(value) {
            _file = value
            id.fileId = value.id
        }

    val fileId: Long get() = requireNotNull(id.fileId) { "fileId is required" }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as EventBanner
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    @Embeddable
    data class Id(
        var eventId: Long? = null,
        var fileId: Long? = null
    ) : java.io.Serializable
}
