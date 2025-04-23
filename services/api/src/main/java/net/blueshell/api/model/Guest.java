package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "guests")
@SQLDelete(sql = "UPDATE guests SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Data
public class Guest  implements BaseModel<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String name;
    @Column
    private String discord;
    @Column
    private String email;
    @Column(name = "created_at")
    private Timestamp createdAt;
    @Column(name = "access_token")
    private String accessToken;

    @OneToOne(mappedBy = "guest")
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
}
