package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.blueshell.api.base.BaseModel;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "redirects")
@SQLDelete(sql = "UPDATE redirects SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at >= NOW()")
@Data
@NoArgsConstructor
public class Redirect implements BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "telemetry_id")
    private Telemetry telemetry;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;

    public Redirect(Telemetry telemetry) {
        this.telemetry = telemetry;
        this.createdAt = Timestamp.from(Instant.now());
    }
}
