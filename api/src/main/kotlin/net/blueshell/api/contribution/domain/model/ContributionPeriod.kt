package net.blueshell.api.contribution.domain.model

import jakarta.persistence.*
import net.blueshell.api.shared.jpa.JpaListener
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate

@Entity
@Table(
    name = "contribution_periods",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_contribution_periods_start_end_deleted_at",
            columnNames = ["start_date", "end_date", "deleted_at"]
        )
    ],
    indexes = [
        Index(name = "idx_contribution_periods_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_contribution_periods_start_date", columnList = "start_date"),
        Index(name = "idx_contribution_periods_end_date", columnList = "end_date"),
        Index(name = "idx_contribution_periods_list_id", columnList = "list_id")
    ]
)
@SQLDelete(sql = "UPDATE contribution_periods SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener::class)
class ContributionPeriod : AuditedAutoIdEntity() {
    @OneToMany(mappedBy = "_contributionPeriod", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private val _contributions: MutableSet<Contribution> = linkedSetOf()
    val contributions: Set<Contribution>
        get() = _contributions

    @Column(name = "start_date", nullable = false)
    lateinit var startDate: LocalDate

    @Column(name = "end_date")
    lateinit var endDate: LocalDate

    @Column(name = "half_year_fee", nullable = false)
    var halfYearFee = 0.0

    @Column(name = "full_year_fee", nullable = false)
    var fullYearFee = 0.0

    @Column(name = "alumni_fee", nullable = false)
    var alumniFee = 0.0

    @Column(name = "list_id")
    var listId: Long? = null
}
