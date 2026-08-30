package net.blueshell.api.esports.persistence

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
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.enums.TeamRole
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

/**
 * One person on one team for one season, hung off the fielding that says the team played it.
 *
 * The team and the season come with [teamSeason] rather than being named again here. Naming
 * them again would be the same fact written twice and able to disagree with itself, and it
 * would leave a line-up for a team nobody fielded writable — a state the pages have no way to
 * draw and no reason to hold. It is unrepresentable instead of merely discouraged.
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
            columnNames = ["team_season_id", "handle", "deleted_at"],
        ),
    ],
    indexes = [
        Index(name = "idx_roster_entry_fielding", columnList = "team_season_id, deleted_at"),
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
    @JoinColumn(name = "team_season_id", nullable = false)
    var teamSeason: TeamSeason,

    @Column(name = "handle", nullable = false, length = 128)
    var handle: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "team_role", nullable = false, length = 16)
    var teamRole: TeamRole = TeamRole.PLAYER,

    /**
     * What this person did, in the team's own words — "Captain", "In-game leader".
     *
     * Decoration on top of [teamRole] rather than a replacement for it: the enum is what a
     * roster is grouped and sorted by, and stays required.
     */
    @Column(name = "role_title", nullable = true, length = 64)
    var roleTitle: String? = null,

    /** A caption rather than a biography, in markdown, capped short enough to stay one. */
    @Column(name = "description", nullable = true, length = 280)
    var description: String? = null,

    /**
     * This entry's own picture, where one has been uploaded.
     *
     * On the entry rather than on the member, so a player can look different across seasons
     * and an entry nobody could be attributed to can still carry one.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "icon_file_id")
    var icon: File? = null,

    @Column(name = "user_id", nullable = true)
    var userId: Long? = null,

    /** The real name as published, kept for identification; publishing it needs consent. */
    @Column(name = "display_name", nullable = true, length = 128)
    var displayName: String? = null,

    /** The order the page lists them in, which is the order they were written in. */
    @Column(name = "sort_index", nullable = false)
    var sortIndex: Int = 0,
) : AuditedAutoIdEntity() {
    /**
     * The team and the season this entry was played under, which the fielding already names.
     *
     * Computed rather than stored, the way every other id beside a reference here is: the
     * fielding is the single source of truth and these save every reader walking to it.
     */
    val teamId: Long? get() = teamSeason.team.id

    val seasonId: Long? get() = teamSeason.season.id
}
