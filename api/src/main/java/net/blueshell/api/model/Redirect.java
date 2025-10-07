package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.blueshell.api.base.BaseModel;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.time.Instant;

@Entity
@Table(name = "redirects")
@SQLDelete(sql = "UPDATE redirects SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
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


    public Redirect(Telemetry telemetry) {
        this.telemetry = telemetry;
        this.createdAt = Timestamp.from(Instant.now());
    }

    @Column(name = "deleted_at", nullable = false)
    private Timestamp deletedAt = Timestamp.valueOf("9999-12-31 23:59:59");
}
