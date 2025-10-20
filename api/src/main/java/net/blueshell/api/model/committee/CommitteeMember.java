package net.blueshell.api.model.committee;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.base.JpaListener;
import net.blueshell.api.model.User;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

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
                @Index(name = "idx_committee_members_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_committee_members_committee_id", columnList = "committee_id"),
                @Index(name = "idx_committee_members_user_id", columnList = "user_id"),
                @Index(name = "idx_committee_members_committee_role", columnList = "committee_id, role")
        }
)
@SQLDelete(sql = "UPDATE committee_members SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener.class)
@Getter
@Setter
@NoArgsConstructor
public class CommitteeMember extends BaseModel {
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
}
