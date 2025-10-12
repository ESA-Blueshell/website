package net.blueshell.api.model.committee;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.base.JpaListener;
import net.blueshell.api.model.User;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.util.Objects;

@Entity
@Table(
        name = "committee_members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_committee_members_committee_user_deleted_at",
                        columnNames = {"committee_id", "user_id", "deleted_at"}
                )
        },
        indexes = {
                @Index(name = "idx_committee_members_committee_id", columnList = "committee_id"),
                @Index(name = "idx_committee_members_user_id", columnList = "user_id"),
                @Index(name = "idx_committee_members_committee_role", columnList = "committee_id, role")
        }
)
@Data
@SQLDelete(sql = "UPDATE committee_members SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener.class)
public class CommitteeMember implements BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false, insertable = false)
    private User user;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "committee_id", nullable = false)
    private Committee committee;

    @Column(name = "role", length = 255)
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
        return Objects.equals(userId, other.userId) &&
                Objects.equals(committee, other.committee);
    }

    @Override
    public int hashCode() {
        // Persisted entities: use the PK
        if (id != null) return 31 + id.hashCode();

        // Transient entities: use the business key
        return Objects.hash(userId, committee);
    }

    @Override
    public String toString() {
        return String.format("CommitteeMember={id: %d, userId: %d, committeeId: %d, role: %s}",
                id, getUser() != null ? getUser().getId() : getUserId(), getCommittee() != null ? getCommittee().getId() : null, role);
    }

    @Column(name = "deleted_at", nullable = false, insertable=false, updatable = false)
    @ColumnDefault("9999-12-31 23:59:59")
    private Timestamp deletedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated
    private Timestamp createdAt;
}
