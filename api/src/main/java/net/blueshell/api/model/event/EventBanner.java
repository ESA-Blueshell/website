package net.blueshell.api.model.event;

import jakarta.persistence.*;
import lombok.*;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.base.JpaListener;
import net.blueshell.api.model.File;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serializable;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@Entity
@Table(
        name = "event_banners",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_event_file", columnNames = {"event_id", "file_id", "deleted_at"})
        },
        indexes = {
                @Index(name = "idx_event_banners_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_event_banners_event", columnList = "event_id"),
                @Index(name = "idx_event_banners_file", columnList = "file_id"),
        }
)
@SQLDelete(sql = "UPDATE event_banners SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener.class)
public class EventBanner implements BaseModel {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, insertable = false, updatable = false)
    private Long eventId;

    @Column(name = "file_id", nullable = false, insertable = false, updatable = false)
    private Long fileId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    @ToString.Exclude
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "file_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_event_banners_file")
    )
    @ToString.Exclude
    private File file;

    @Column(name = "deleted_at", nullable = false, insertable=false, updatable = false)
    @ColumnDefault("9999-12-31 23:59:59")
    private Timestamp deletedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @org.hibernate.annotations.Generated
    private Timestamp createdAt;
}
