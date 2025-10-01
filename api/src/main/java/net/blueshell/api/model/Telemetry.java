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
@Table(name = "telemetries")
@SQLDelete(sql = "UPDATE telemetries SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at >= NOW()")
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
}
