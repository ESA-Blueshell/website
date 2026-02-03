package net.blueshell.api.model.event

import jakarta.persistence.*
import net.blueshell.api.base.BaseModel
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
class EventPicture : BaseModel() {
    @OneToOne
    @JoinColumn(name = "picture_id", nullable = false)
    lateinit var picture: File

    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false)
    lateinit var event: Event
}
