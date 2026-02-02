package net.blueshell.api.model

import jakarta.persistence.*
import lombok.Data
import lombok.EqualsAndHashCode
import lombok.NoArgsConstructor
import lombok.ToString
import net.blueshell.api.base.BaseModel
import net.blueshell.api.base.JpaListener
import net.blueshell.api.common.enums.FileType
import net.blueshell.api.model.event.EventBanner
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "files",
    uniqueConstraints = [UniqueConstraint(name = "uk_files_path_deleted_at", columnNames = ["path", "deleted_at"])],
    indexes = [Index(name = "idx_files_deleted_at", columnList = "deleted_at"), Index(
        name = "idx_files_uploader_id",
        columnList = "uploader_id"
    ), Index(name = "idx_files_media_type", columnList = "media_type"), Index(
        name = "idx_files_type",
        columnList = "type"
    ), Index(name = "idx_files_created_at", columnList = "created_at")]
)
@SQLDelete(sql = "UPDATE files SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@NoArgsConstructor
@EntityListeners(JpaListener::class)
@ToString(onlyExplicitlyIncluded = true, callSuper = true)
class File : BaseModel() {
    @Column(nullable = false)
    @ToString.Include
    private var name: String? = null

    @Column(nullable = false)
    @ToString.Include
    private var path: String? = null

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", nullable = false, insertable = false, updatable = false)
    private val uploader: User? = null

    @Column(name = "uploader_id", nullable = false)
    @ToString.Include
    private var uploaderId: Long = 0

    @Column(name = "media_type", nullable = false)
    @ToString.Include
    private var mediaType: String? = null

    @Column(name = "size")
    @ToString.Include
    private var size: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    @ToString.Include
    private var type: FileType? = null

    @OneToMany(mappedBy = "file")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private val eventBanners: MutableSet<EventBanner?>? = null
}
