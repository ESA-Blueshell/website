package net.blueshell.api.file.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import net.blueshell.api.shared.model.asRef
import net.blueshell.api.event.persistence.EventBanner
import net.blueshell.api.user.persistence.User
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "files",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_files_path_deleted_at", columnNames = ["path", "deleted_at"])
    ],
    indexes = [
        Index(name = "idx_files_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_files_uploader_id", columnList = "uploader_id"),
        Index(name = "idx_files_media_type", columnList = "media_type"),
        Index(name = "idx_files_type", columnList = "type"),
        Index(name = "idx_files_created_at", columnList = "created_at")
    ]
)
@SQLDelete(sql = "UPDATE files SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class File : AuditedAutoIdEntity() {
    @Column(nullable = false)
    lateinit var name: String

    @Column(nullable = false)
    lateinit var path: String

    @field:OneToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "uploader_id", nullable = false)
    private var _uploader: User? = null
    var uploader: User
        get() = requireNotNull(_uploader) { "Uploader is required" }
        set(value) {
            _uploader = value
            uploaderId = _uploader?.id ?: uploaderId
        }

    @field:Column(name = "uploader_id", nullable = false, updatable = false, insertable = false)
    var uploaderId: Long = 0
        get() = _uploader?.id ?: field
        set(value) {
            field = value
            // Only override the reference, if the ref exists and is different from current
            if (value != 0L && value != _uploader?.id) {
                _uploader = User::class.asRef(value)
            }
        }

    @Column(name = "media_type", nullable = false)
    lateinit var mediaType: String

    @Column(name = "size")
    var size: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    lateinit var type: FileType

    @OneToMany(mappedBy = "_file", fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private val _eventBanners: MutableSet<EventBanner> = linkedSetOf()
    val eventBanners: Set<EventBanner>
        get() = _eventBanners
}
