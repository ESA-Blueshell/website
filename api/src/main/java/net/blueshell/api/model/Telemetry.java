package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.common.enums.PlatformType;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

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
@SQLDelete(sql = "UPDATE telemetries SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@Getter
@Setter
@NoArgsConstructor
public class Telemetry extends BaseModel {
    @Column(nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private PlatformType platform;

    @OneToMany(mappedBy = "telemetry", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Redirect> redirects;

    public Telemetry(PlatformType platform, String url) {
        this.platform = platform;
        this.url = url;
    }
}
