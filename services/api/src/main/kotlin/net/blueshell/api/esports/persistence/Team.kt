package net.blueshell.api.esports.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

/**
 * A team the association fields, in whatever games it plays.
 *
 * The team outlives its rosters: who plays for it is a [TeamRosterEntry] per fielding, so a
 * team that keeps its name through a full change of line-up stays one team. It outlives the
 * game as well — BS HyperS played CS:GO until 2023 and CS2 after it, and for one season both —
 * so the game belongs to the [TeamSeason] rather than here, and the pool of teams is shared
 * across every game while a line-up is not.
 *
 * What is left is the identity: a name, unique across the association, and the logo drawn
 * beside it. The banner is on the fielding, because the art a team is drawn with is
 * game-flavoured while the logo is the team itself.
 */
@Entity
@Table(
    name = "team",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_team_name", columnNames = ["name", "deleted_at"]),
    ],
    indexes = [
        Index(name = "idx_team_deleted_at", columnList = "deleted_at"),
    ],
)
@SQLDelete(sql = "UPDATE team SET deleted_at = NOW(6), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class Team(
    @Column(name = "name", nullable = false, length = 128)
    var name: String,

    /**
     * The team's logo, drawn there beside the name.
     *
     * Optional, and shipped with nothing: a game's logo existed in the frontend and moved here,
     * a team's never existed at all. A team without one is drawn as its name over its banner,
     * which is every team on the day this ships.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "icon_file_id")
    var icon: File? = null,
) : AuditedAutoIdEntity()
