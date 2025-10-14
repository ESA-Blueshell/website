package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.base.JpaListener;
import net.blueshell.api.common.enums.FileType;
import net.blueshell.api.model.event.EventBanner;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(
        name = "files",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_files_path_deleted_at", columnNames = {"path", "deleted_at"})
        },
        indexes = {
                @Index(name = "idx_files_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_files_uploader_id", columnList = "uploader_id"),
                @Index(name = "idx_files_media_type", columnList = "media_type"),
                @Index(name = "idx_files_type", columnList = "type"),
                @Index(name = "idx_files_created_at", columnList = "created_at")
        }
)
@SQLDelete(sql = "UPDATE files SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@Data
@EntityListeners(JpaListener.class)
public class File implements BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String path;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", nullable = false, insertable = false, updatable = false)
    private User uploader;

    @Column(name = "uploader_id", nullable = false)
    private long uploaderId;

    @Column(name = "media_type", nullable = false)
    private String mediaType;

    @Column(name = "size")
    private Long size;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private FileType type;

    @OneToMany(mappedBy = "file")
    private Set<EventBanner> eventBanners;
    @Column(name = "deleted_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("9999-12-31 23:59:59")
    private Timestamp deletedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated
    private Timestamp createdAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        File file = (File) o;
        return Objects.equals(id, file.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
