package net.blueshell.api.model.contribution

import jakarta.persistence.*
import net.blueshell.api.base.BaseModel
import net.blueshell.api.base.JpaListener
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate

@Entity
@Table(
    name = "contribution_periods",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_contribution_periods_start_end_deleted_at",
        columnNames = ["start_date", "end_date", "deleted_at"]
    )],
    indexes = [Index(
        name = "idx_contribution_periods_deleted_at",
        columnList = "deleted_at"
    ), Index(
        name = "idx_contribution_periods_start_date",
        columnList = "start_date"
    ), Index(
        name = "idx_contribution_periods_end_date",
        columnList = "end_date"
    ), Index(name = "idx_contribution_periods_list_id", columnList = "list_id")]
)
@SQLDelete(sql = "UPDATE contribution_periods SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener::class)
class ContributionPeriod : BaseModel() {
    @OneToMany(mappedBy = "contributionPeriod", cascade = [CascadeType.ALL])
    val contributions: MutableSet<Contribution?>? = null

    @Column(name = "start_date", nullable = false)
    lateinit var startDate: LocalDate

    @Column(name = "end_date")
    var endDate: LocalDate? = null

    @Column(name = "half_year_fee", nullable = false)
    var halfYearFee = 0.0

    @Column(name = "full_year_fee", nullable = false)
    var fullYearFee = 0.0

    @Column(name = "alumni_fee", nullable = false)
    var alumniFee = 0.0

    @Column(name = "list_id")
    var listId: Long? = null
}
