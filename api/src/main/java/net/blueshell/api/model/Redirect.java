package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.blueshell.api.base.BaseModel;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.time.Instant;

@Entity
@Table(
        name = "redirects",
        indexes = {
                @Index(name = "idx_redirects_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_redirects_telemetry_id", columnList = "telemetry_id"),
                @Index(name = "idx_redirects_created_at", columnList = "created_at")
        }
)
@SQLDelete(sql = "UPDATE redirects SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@Getter
@Setter
@NoArgsConstructor
public class Redirect extends BaseModel {
    @ManyToOne
    @JoinColumn(name = "telemetry_id", nullable = false)
    private Telemetry telemetry;

    public Redirect(Telemetry telemetry) {
        this.telemetry = telemetry;
    }
}
