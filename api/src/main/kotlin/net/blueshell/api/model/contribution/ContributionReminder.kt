package net.blueshell.api.model.contribution

import jakarta.persistence.*
import net.blueshell.api.base.JpaListener
import net.blueshell.api.base.entity.AuditedAutoIdEntity
import net.blueshell.api.model.User
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "contribution_reminders",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_contribution_reminders_user_period_deleted_at",
        columnNames = ["user_id", "contribution_period_id", "deleted_at"]
    )],
    indexes = [
        Index(name = "idx_contribution_reminders_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_contribution_reminders_created_at", columnList = "created_at"),
        Index(name = "idx_contribution_reminders_user_id", columnList = "user_id, deleted_at"),
        Index(
            name = "idx_contribution_reminders_contribution_period_id",
            columnList = "contribution_period_id, deleted_at"
        )
    ]
)
@SQLDelete(sql = "UPDATE contribution_reminders SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener::class)
class ContributionReminder : AuditedAutoIdEntity() {
    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "user_id", insertable = false, updatable = false, nullable = false)
    private var _user: User? = null
    var user: User
        get() = requireNotNull(_user) { "User is required" }
        set(value) {
            _user = value
            userId = value.id ?: userId
        }

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "contribution_period_id", insertable = false, updatable = false, nullable = false)
    private var _contributionPeriod: ContributionPeriod? = null
    var contributionPeriod: ContributionPeriod
        get() = requireNotNull(_contributionPeriod) { "Contribution period is required" }
        set(value) {
            _contributionPeriod = value
            contributionPeriodId = value.id ?: contributionPeriodId
        }

    @Column(name = "contribution_period_id", nullable = false)
    var contributionPeriodId: Long = 0
}
