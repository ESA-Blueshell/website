package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.base.JpaListener;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.util.Objects;

@Entity
@Table(name = "committee_members")
@Data
@SQLDelete(sql = "UPDATE committee_members SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(JpaListener.class)
public class CommitteeMember implements BaseModel<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false) // allow Hibernate to write FK
    private User user;

    @Column(name = "user_id", updatable = false, insertable = false)
    private Long userId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "committee_id", nullable = false) // allow Hibernate to write FK
    private Committee committee;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;

    @Column(name = "role")
    private String role;

    public CommitteeMember() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommitteeMember other)) return false;

        // Persisted entities: compare primary key
        if (id != null && other.id != null) {
            return id.equals(other.id);
        }

        // Transient entities: compare the natural/business key
        return Objects.equals(user, other.user) &&
                Objects.equals(committee, other.committee);
    }

    @Override
    public int hashCode() {
        // Persisted entities: use the PK
        if (id != null) return 31 + id.hashCode();

        // Transient entities: use the business key
        return Objects.hash(user, committee);
    }

    @Override
    public String toString() {
        return String.format("CommitteeMember={id: %d, userId: %d, committeeId: %d, role: %s}",
                id, getUser() != null ? getUser().getId() : null, getCommittee() != null ? getCommittee().getId() : null, role);
    }
}
