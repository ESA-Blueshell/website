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
import java.util.UUID;

@Entity
@Table(name = "telemetries")
@SQLDelete(sql = "UPDATE telemetries SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Data
@NoArgsConstructor
public class Telemetry implements BaseModel<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String url;

    private PlatformType platform;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;

    public Telemetry(PlatformType platform, String url) {
        this.platform = platform;
        this.url = url;
        this.createdAt = Timestamp.from(Instant.now());
    }
}
