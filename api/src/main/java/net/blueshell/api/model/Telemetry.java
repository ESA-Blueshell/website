package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.common.enums.PlatformType;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;

@Entity
@Table(
        name = "telemetries",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_telemetries_platform_url_deleted_at",
                        columnNames = {"platform", "url", "deleted_at"}
                )
        },
        indexes = {
                @Index(name = "idx_telemetries_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_telemetries_platform", columnList = "platform"),
                @Index(name = "idx_telemetries_url", columnList = "url"),
                @Index(name = "idx_telemetries_created_at", columnList = "created_at")
        }
)
@SQLDelete(sql = "UPDATE telemetries SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@Data
@NoArgsConstructor
public class Telemetry implements BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private PlatformType platform;

    @OneToMany(mappedBy = "telemetry", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Redirect> redirects;

    public Telemetry(PlatformType platform, String url) {
        this.platform = platform;
        this.url = url;
    }

    @Column(name = "deleted_at", nullable = false, insertable=false, updatable = false)
    @ColumnDefault("9999-12-31 23:59:59")
    private Timestamp deletedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated
    private Timestamp createdAt;
}
