package net.blueshell.api.model.contribution;

import jakarta.persistence.*;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.ToString;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.base.JpaListener;
import net.blueshell.api.model.User;
import org.hibernate.annotations.*;

import java.sql.Timestamp;
import java.util.Objects;

@Entity
@Table(
        name = "contributions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_contributions_user_period_deleted_at",
                        columnNames = {"user_id", "contribution_period_id", "deleted_at"}
                )
        },
        indexes = {
                @Index(name = "idx_contributions_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_contributions_user_id", columnList = "user_id"),
                @Index(name = "idx_contributions_contribution_period_id", columnList = "contribution_period_id"),
                @Index(name = "idx_contributions_paid", columnList = "paid"),
                @Index(name = "idx_contributions_reminded_at", columnList = "reminded_at")
        }
)
@Data
@SQLDelete(sql = "UPDATE contributions SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener.class)
public class Contribution implements BaseModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", insertable = false, updatable = false, nullable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    @ToString.Exclude
    private User user;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne
    @JoinColumn(name = "contribution_period_id", insertable = false, updatable = false, nullable = false)
    private ContributionPeriod contributionPeriod;

    @Column(name = "contribution_period_id", nullable = false)
    private Long contributionPeriodId;
    @Column(name = "deleted_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("9999-12-31 23:59:59")
    private Timestamp deletedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated
    private Timestamp createdAt;

    public Contribution() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Contribution that = (Contribution) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
