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
 * A team the association fields in one game.
 *
 * The team outlives its rosters: who plays for it is a [TeamRosterEntry] per season, so a
 * team that keeps its name through a full change of line-up stays one team.
 */
@Entity
@Table(
    name = "team",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_team_game_name", columnNames = ["game", "name", "deleted_at"]),
    ],
    indexes = [
        Index(name = "idx_team_game", columnList = "game, deleted_at"),
        Index(name = "idx_team_deleted_at", columnList = "deleted_at"),
    ],
)
@SQLDelete(sql = "UPDATE team SET deleted_at = NOW(6), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class Team(
    @Column(name = "game", nullable = false, length = 32)
    var game: String,

    @Column(name = "name", nullable = false, length = 128)
    var name: String,

    /**
     * The team's own banner, drawn in the slice for it on its game's page.
     *
     * The only picture a team has. It was a filename in the frontend's assets directory with an
     * upload beside it, which meant two fields meaning the same thing; the site ships its own
     * art now, so every team has an upload and the filename had nothing left to fall back to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "banner_file_id")
    var banner: File? = null,

    /**
     * The team's logo, drawn in that same slice beside the name.
     *
     * Optional, and shipped with nothing: a game's logo existed in the frontend and moved here,
     * a team's never existed at all. A team without one is drawn as its name over its banner,
     * which is every team on the day this ships.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "icon_file_id")
    var icon: File? = null,
) : AuditedAutoIdEntity()
