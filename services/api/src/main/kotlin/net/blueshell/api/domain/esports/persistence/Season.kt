package net.blueshell.api.domain.esports.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate

/**
 * A stretch of play a roster belongs to — two of them in a board year.
 *
 * Deliberately not a contribution period: a team can outlive a period, span two, or run for
 * half of one, so tying a roster to a period would force a lie in each of those cases. The
 * dates are the season's own, and questions about a period ask which seasons overlap it.
 */
@Entity
@Table(
    name = "season",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_season_name", columnNames = ["name", "deleted_at"]),
    ],
    indexes = [
        Index(name = "idx_season_dates", columnList = "start_date, end_date"),
        Index(name = "idx_season_deleted_at", columnList = "deleted_at"),
    ],
)
@SQLDelete(sql = "UPDATE season SET deleted_at = NOW(6), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class Season(
    @Column(name = "name", nullable = false, length = 64)
    var name: String,

    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,

    @Column(name = "end_date", nullable = false)
    var endDate: LocalDate,
) : AuditedAutoIdEntity() {
    /** Inclusive on both ends, which is how a season is written down and read. */
    fun overlaps(from: LocalDate, to: LocalDate): Boolean =
        !startDate.isAfter(to) && !endDate.isBefore(from)
}
