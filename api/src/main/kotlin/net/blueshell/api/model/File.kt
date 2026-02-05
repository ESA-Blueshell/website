package net.blueshell.api.model

import jakarta.persistence.*
import net.blueshell.api.common.jpa.JpaListener
import net.blueshell.api.model.base.AuditedAutoIdEntity
import net.blueshell.api.common.enums.FileType
import net.blueshell.api.model.event.EventBanner
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
@EntityListeners(JpaListener::class)
open class File : AuditedAutoIdEntity() {
    @Column(nullable = false)
    lateinit var name: String

    @Column(nullable = false)
    lateinit var path: String

    @field:OneToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "uploader_id", nullable = false, insertable = false, updatable = false)
    private var _uploader: User? = null
    var uploader: User
        get() = requireNotNull(_uploader) { "Uploader is required" }
        set(value) {
            _uploader = value
            uploaderId = value.id ?: uploaderId
        }

    @Column(name = "uploader_id", nullable = false)
    var uploaderId: Long = 0

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
