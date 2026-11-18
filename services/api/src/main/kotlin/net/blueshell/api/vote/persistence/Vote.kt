package net.blueshell.api.vote.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import net.blueshell.api.shared.model.SoftDelete
import org.hibernate.annotations.SQLRestriction
import java.time.Instant

/**
 * One kind's vote for one year. It runs on its dates alone: voting between [opensAt] and
 * [closesAt], and counted at [revealAt], which sets [revealedAt] and [ballotCount]. A ballot holds
 * up to [picks] picks, a number the board sets per vote.
 */
@Entity
@Table(
    name = "votes",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_votes_kind_year_deleted_at", columnNames = ["kind", "year", "deleted_at"]),
    ],
)
@SQLRestriction(SoftDelete.ACTIVE)
class Vote(
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    val kind: VoteKind,
    @Column(name = "year", nullable = false)
    val year: Int,
    @Column(name = "opens_at", nullable = false)
    var opensAt: Instant,
    @Column(name = "closes_at", nullable = false)
    var closesAt: Instant,
    @Column(name = "reveal_at", nullable = false)
    var revealAt: Instant,
    @Column(name = "picks", nullable = false)
    var picks: Int,
) : AuditedAutoIdEntity() {
    @Column(name = "ballot_count")
    var ballotCount: Int? = null

    @Column(name = "revealed_at")
    var revealedAt: Instant? = null
}
