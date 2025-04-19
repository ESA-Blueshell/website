package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.util.Objects;

@Entity
@Table(name = "committee_members")
@Data
@SQLDelete(sql = "UPDATE committee_members SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class CommitteeMember implements BaseModel<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "committee_id", nullable = false)
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
        if (!(o instanceof CommitteeMember that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, getUserId(), getCommitteeId(), role);
    }

    @Override
    public String toString() {
        return String.format("CommitteeMember={id: %d, userId: %d, committeeId: %d, role: %s}",
                id, getUserId(), getCommitteeId(), role);
    }

    public Long getUserId() {
        return user != null ? user.getId() : null;
    }

    public Long getCommitteeId(){
        return committee != null ? committee.getId() : null;
    }
}
