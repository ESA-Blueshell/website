package net.blueshell.api.model.contribution;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.base.JpaListener;
import net.blueshell.api.model.User;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

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
                @Index(name = "idx_contributions_created_at", columnList = "created_at")
        }
)
@SQLDelete(sql = "UPDATE contributions SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Contribution extends BaseModel {
    @ManyToOne
    @JoinColumn(name = "user_id", insertable = false, updatable = false, nullable = false)
    private User user;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne
    @JoinColumn(name = "contribution_period_id", insertable = false, updatable = false, nullable = false)
    private ContributionPeriod contributionPeriod;

    @Column(name = "contribution_period_id", nullable = false)
    private Long contributionPeriodId;
}
