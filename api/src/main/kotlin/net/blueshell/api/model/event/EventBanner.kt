package net.blueshell.api.model.event

import jakarta.persistence.*
import net.blueshell.api.base.BaseModel
import net.blueshell.api.base.JpaListener
import net.blueshell.api.model.File
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "event_banners",
    uniqueConstraints = [UniqueConstraint(name = "uk_event_file", columnNames = ["event_id", "file_id", "deleted_at"])],
    indexes = [Index(
        name = "idx_event_banners_deleted_at",
        columnList = "deleted_at"
    ), Index(name = "idx_event_banners_event", columnList = "event_id"), Index(
        name = "idx_event_banners_file",
        columnList = "file_id"
    )]
)
@SQLDelete(sql = "UPDATE event_banners SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener::class)
class EventBanner : BaseModel() {
    @Column(name = "event_id", nullable = false)
    var eventId: Long = 0

    @Column(name = "file_id", nullable = false)
    var fileId: Long = 0

    @field:OneToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "event_id", nullable = false, insertable = false, updatable = false)
    private var _event: Event? = null
    var event: Event
        get() = requireNotNull(_event) { "Event is required" }
        set(value) {
            _event = value
            eventId = value.id ?: eventId
        }

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(
        name = "file_id",
        nullable = false,
        foreignKey = ForeignKey(name = "fk_event_banners_file"),
        insertable = false,
        updatable = false
    )
    private var _file: File? = null
    var file: File
        get() = requireNotNull(_file) { "File is required" }
        set(value) {
            _file = value
            fileId = value.id ?: fileId
        }
}
