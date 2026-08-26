package net.blueshell.api.domain.esports.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.blueshell.api.shared.enums.TeamRole
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

/**
 * One person on one team for one season.
 *
 * `userId` is nullable and a plain Long. Nullable because most of the recovered history is a
 * handle and nothing else, and an entry nobody can attribute is still the roster that played;
 * plain because a roster has no business walking the user entity graph, the same reason
 * cohort membership stores its user that way.
 *
 * [handle] is what this entry was published under. A linked entry prefers the user's current
 * handle for the game, so a rename lands on every season at once and this column is the
 * fallback and the historical record.
 */
@Entity
@Table(
    name = "team_roster_entry",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_roster_entry",
            columnNames = ["team_id", "season_id", "handle", "deleted_at"],
        ),
    ],
    indexes = [
        Index(name = "idx_roster_entry_season", columnList = "season_id, deleted_at"),
        Index(name = "idx_roster_entry_user", columnList = "user_id, deleted_at"),
        Index(name = "idx_roster_entry_deleted_at", columnList = "deleted_at"),
    ],
)
@SQLDelete(
    sql = "UPDATE team_roster_entry SET deleted_at = NOW(6), version = version + 1 WHERE id = ? AND version = ?",
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class TeamRosterEntry(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    var team: Team,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    var season: Season,

    @Column(name = "handle", nullable = false, length = 128)
    var handle: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "team_role", nullable = false, length = 16)
    var teamRole: TeamRole = TeamRole.PLAYER,

    @Column(name = "user_id", nullable = true)
    var userId: Long? = null,

    /** The real name as published, kept for identification; publishing it needs consent. */
    @Column(name = "display_name", nullable = true, length = 128)
    var displayName: String? = null,

    /** The order the page lists them in, which is the order they were written in. */
    @Column(name = "sort_index", nullable = false)
    var sortIndex: Int = 0,
) : AuditedAutoIdEntity()
