package net.blueshell.api.domain.event.persistence

import jakarta.persistence.*
import net.blueshell.api.domain.file.persistence.File
import net.blueshell.api.shared.model.AuditedSoftDeleteEntity
import net.blueshell.api.shared.model.Identifiable
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

    @MapsId("eventId")
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    lateinit var event: Event
        internal set

    val eventId: Long
        get() = id.eventId ?: 0

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "file_id",
        foreignKey = ForeignKey(name = "fk_event_banners_file"),
        nullable = false,
        insertable = false,
        updatable = false,
    )
    val file: File? = null

    var fileId: Long
        set(value) {
            id.fileId = value
        }
        get() = id.fileId ?: 0

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
        @Column(name = "file_id", nullable = false)
        var fileId: Long? = null
    ) : java.io.Serializable
}
