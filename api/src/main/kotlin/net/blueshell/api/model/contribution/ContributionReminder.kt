package net.blueshell.api.model.contribution

import jakarta.persistence.*
import net.blueshell.api.base.BaseModel
import net.blueshell.api.base.JpaListener
import net.blueshell.api.model.User
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import kotlin.properties.Delegates

@Entity
@Table(
    name = "contribution_reminders",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_contribution_reminders_user_period_deleted_at",
        columnNames = ["user_id", "contribution_period_id", "deleted_at"]
    )],
    indexes = [Index(
        name = "idx_contribution_reminders_deleted_at",
        columnList = "deleted_at"
    ), Index(
        name = "idx_contribution_reminders_created_at",
        columnList = "created_at"
    ), Index(
        name = "idx_contribution_reminders_user_id",
        columnList = "user_id, deleted_at"
    ), Index(
        name = "idx_contribution_reminders_contribution_period_id",
        columnList = "contribution_period_id, deleted_at"
    )]
)
@SQLDelete(sql = "UPDATE contribution_reminders SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener::class)
class ContributionReminder : BaseModel() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false, nullable = false)
    lateinit var user: User

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contribution_period_id", insertable = false, updatable = false, nullable = false)
    lateinit var contributionPeriod: ContributionPeriod

    @Column(name = "contribution_period_id", nullable = false)
    var contributionPeriodId: Long = 0
}
