package net.blueshell.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.util.Objects;

@Entity
@Table(
        name = "sponsors",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sponsors_name_deleted_at", columnNames = {"name", "deleted_at"}),
                @UniqueConstraint(name = "uk_sponsors_logo_deleted_at", columnNames = {"logo_id", "deleted_at"})
        },
        indexes = {
                @Index(name = "idx_sponsors_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_sponsors_logo_id", columnList = "logo_id")
        }
)

@SQLDelete(sql = "UPDATE sponsors SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@Data
public class Sponsor implements BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 4095)
    private String description;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "logo_id", nullable = false)
    private File picture;
    @Column(name = "deleted_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("9999-12-31 23:59:59")
    private Timestamp deletedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated
    private Timestamp createdAt;

    @JsonProperty("picture")
    public long getPictureId() {
        return getPicture() == null ? 0 : getPicture().getId();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Sponsor sponsor = (Sponsor) o;
        return Objects.equals(id, sponsor.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
