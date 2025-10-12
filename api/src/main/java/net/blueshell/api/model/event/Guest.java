package net.blueshell.api.model.event;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

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
@SQLDelete(sql = "UPDATE guests SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@Data
public class Guest implements BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String discord;

    @Column(nullable = false)
    private String email;

    @Column(name = "access_token", nullable = false)
    private String accessToken;

    @OneToOne(mappedBy = "guest", cascade = CascadeType.ALL)
    private EventSignUp eventSignUp;

    public Guest() {
    }

    public Guest(String name, String discord, String email) {
        this.name = name;
        this.discord = discord;
        this.email = email;
        this.createdAt = Timestamp.from(Instant.now());
        this.accessToken = BCrypt.hashpw(name + discord + email + this.createdAt, BCrypt.gensalt())
                .replace("/", "");
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Guest user = (Guest) o;
        return id == user.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Column(name = "deleted_at", nullable = false, insertable=false, updatable = false)
    @ColumnDefault("9999-12-31 23:59:59")
    private Timestamp deletedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated
    private Timestamp createdAt;
}
