package net.blueshell.api.file.persistence

import jakarta.persistence.*
import net.blueshell.api.user.persistence.User
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.BatchSize
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

    /**
     * How wide the image is, where it is one and its size could be read.
     *
     * Null is an answer rather than a gap: a format nothing here can measure is still stored,
     * and a caller draws it without reserving its space.
     */
    @Column(name = "width")
    var width: Int? = null,

    @Column(name = "height")
    var height: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    var type: FileType,

    /**
     * The picture this is a narrower copy of, or nothing where this is the picture itself.
     *
     * A copy is a file in its own right — it is served by the same route and read by the same
     * rules — and this is what says which picture it stands for.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_file_id")
    var source: File? = null,

    /** The width this copy was written at, and half of the address it was written to. */
    @Column(name = "rendition_width")
    var renditionWidth: Int? = null,
) : AuditedAutoIdEntity() {

    @OneToMany(mappedBy = "source", fetch = FetchType.LAZY)
    @OrderBy("renditionWidth ASC")
    @BatchSize(size = 64)
    private val _renditions: MutableList<File> = mutableListOf()

    /**
     * The widths this picture is stored at, narrowest first.
     *
     * Read-only: a width is written by deriving it from this picture, never by being added to
     * a list on it.
     *
     * Batched rather than fetched one collection at a time: a page draws a poster, a banner and
     * an icon per player, and each of those asking for its own widths on its own would be a
     * query per image for a list a single round trip could have answered.
     */
    val renditions: List<File>
        get() = _renditions

    val uploaderId: Long
        get() = uploader.id ?: 0

    /** Whether this is a narrower copy of another picture rather than a picture somebody uploaded. */
    val isRendition: Boolean get() = renditionWidth != null
}
