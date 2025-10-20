package net.blueshell.api.model.event;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.blueshell.api.base.BaseModel;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(
        name = "guests",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_guests_access_token_deleted_at", columnNames = {"access_token", "deleted_at"})
        },
        indexes = {
                @Index(name = "idx_guests_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_guests_name", columnList = "name"),
                @Index(name = "idx_guests_discord", columnList = "discord"),
                @Index(name = "idx_guests_created_at", columnList = "created_at")
        }
)
@SQLDelete(sql = "UPDATE guests SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@Getter
@Setter
@NoArgsConstructor
public class Guest extends BaseModel {
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String discord;

    @Column(nullable = false)
    private String email;

    @Column
    private String phoneNumber;

    @Column(name = "access_token", nullable = false)
    private String accessToken;
}
