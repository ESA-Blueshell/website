package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.common.enums.PlatformType;
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

    private String url;

    private PlatformType platform;

    @Column(name = "created_at")
    private Timestamp createdAt;


    @OneToMany(mappedBy = "telemetry", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Redirect> redirects;

    public Telemetry(PlatformType platform, String url) {
        this.platform = platform;
        this.url = url;
        this.createdAt = Timestamp.from(Instant.now());
    }

    @Column(name = "deleted_at", nullable = false, insertable=false, updatable = false)
    private Timestamp deletedAt = Timestamp.valueOf("9999-12-31 23:59:59");
}
