package net.blueshell.api.esports.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

/**
 * A game the association ran in a season, whether or not a team has been fielded in it yet.
 *
 * Entering a game and fielding a team in it are decided weeks apart, so this holds "we are
 * playing League this season, the squad is still forming" — a game recorded before anybody is
 * named to it. A visitor never sees one alone: a game becomes public in a season once a team
 * plays it, and this
 * is the board's view, and doubles as its list of what is left to do this season.
 */
@Entity
@Table(
    name = "season_game",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_season_game", columnNames = ["season_id", "game", "deleted_at"]),
    ],
    indexes = [Index(name = "idx_season_game_deleted_at", columnList = "deleted_at")],
)
@SQLDelete(sql = "UPDATE season_game SET deleted_at = NOW(6), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class SeasonGame(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    var season: Season,

    @Column(name = "game", nullable = false, length = 32)
    var game: String,
) : AuditedAutoIdEntity()
