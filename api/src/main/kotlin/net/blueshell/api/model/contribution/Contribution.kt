package net.blueshell.api.model.contribution

import jakarta.persistence.*
import net.blueshell.api.common.jpa.JpaListener
import net.blueshell.api.model.User
import net.blueshell.api.model.base.AuditedSoftDeleteEntity
import net.blueshell.api.model.base.Identifiable
import net.blueshell.api.model.base.asRef
import org.hibernate.Hibernate
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "contributions",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_contributions_user_period_deleted_at",
            columnNames = ["user_id", "contribution_period_id", "deleted_at"]
        )
    ],
    indexes = [
        Index(name = "idx_contributions_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_contributions_user_id", columnList = "user_id"),
        Index(name = "idx_contributions_contribution_period_id", columnList = "contribution_period_id"),
        Index(name = "idx_contributions_created_at", columnList = "created_at")
    ]
)
@SQLDelete(
    sql = """
      UPDATE contributions
      SET deleted_at = NOW(), version = version + 1
      WHERE user_id = ? AND contribution_period_id = ? AND version = ?
    """
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener::class)
class Contribution(
    @EmbeddedId
    override var id: Id = Id()
) : AuditedSoftDeleteEntity(), Identifiable<Contribution.Id> {

    @get:Transient
    @set:Transient
    var userId: Long
        get() = requireNotNull(id.userId) { "userId is required" }
        set(value) {
            id.userId = value
            if (_user?.id != value) {
                _user = User::class.asRef(value)
            }
        }

    @get:Transient
    @set:Transient
    var contributionPeriodId: Long
        get() = requireNotNull(id.contributionPeriodId) { "contributionPeriodId is required" }
        set(value) {
            id.contributionPeriodId = value
            if (_contributionPeriod?.id != value) {
                _contributionPeriod = ContributionPeriod::class.asRef(value)
            }
        }

    @field:MapsId("userId")
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "user_id", nullable = false)
    private var _user: User? = null
    var user: User
        get() = requireNotNull(_user) { "User is required" }
        set(value) {
            _user = value
            value.id?.let { userId = it }
        }

    @field:MapsId("contributionPeriodId")
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "contribution_period_id", nullable = false)
    private var _contributionPeriod: ContributionPeriod? = null
    var contributionPeriod: ContributionPeriod
        get() = requireNotNull(_contributionPeriod) { "Contribution period is required" }
        set(value) {
            _contributionPeriod = value
            value.id?.let { contributionPeriodId = it }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as Contribution
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    @Embeddable
    data class Id(
        var userId: Long? = null,
        var contributionPeriodId: Long? = null
    ) : java.io.Serializable
}
