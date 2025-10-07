package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.common.enums.FileType;
import net.blueshell.api.model.event.EventBanner;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.util.Objects;

@Entity
@Table(name = "files")
@SQLDelete(sql = "UPDATE files SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@Data
public class File implements BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String path;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", nullable = false, insertable = false, updatable = false)
    private User uploader;

    @Column(name = "uploader_id")
    private long uploaderId;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "media_type")
    private String mediaType;

    @Column(name = "size")
    private Long size;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private FileType type;

    @OneToOne(mappedBy = "file")
    private EventBanner eventBanner;

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
