package net.blueshell.fileservice.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import net.blueshell.db.BaseModel;
import net.blueshell.enums.FileType;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.util.Objects;

@Entity
@Table(name = "files")
@SQLDelete(sql = "UPDATE files SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Data
public class File  implements BaseModel<Long> {

    @Getter
    @Setter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Getter
    private String name;

    @Setter
    @Getter
    private String url;

    @Column(name = "uploader_id", insertable = false, updatable = false)
    private long uploaderId;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "media_type")
    private String mediaType;

    @Column(name = "size")
    private Long size;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private FileType fileType;

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
