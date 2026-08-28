package net.blueshell.api.file.persistence

import jakarta.persistence.*
import net.blueshell.api.user.persistence.User
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.model.AuditedAutoIdEntity
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
class File(
    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var path: String,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id", nullable = false)
    var uploader: User,

    @Column(name = "media_type", nullable = false)
    var mediaType: String,

    @Column(name = "size")
    var size: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    var type: FileType,
) : AuditedAutoIdEntity() {

    val uploaderId: Long
        get() = uploader.id ?: 0
}
