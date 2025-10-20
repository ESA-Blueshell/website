package net.blueshell.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
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

@SQLDelete(sql = "UPDATE sponsors SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@Getter
@Setter
@NoArgsConstructor
public class Sponsor extends BaseModel {
    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 4095)
    private String description;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "logo_id", nullable = false, insertable = false, updatable = false)
    private File picture;

    @Column(name = "logo_id")
    private Long pictureId;
}
