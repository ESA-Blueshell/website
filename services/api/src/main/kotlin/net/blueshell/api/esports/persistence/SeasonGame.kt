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
 * Entering a game and fielding a team in it are decided weeks apart: the board settles which
 * games the association will play when the season is planned, and the squads form over the
 * weeks after. Without this, "we are playing League of Legends this season, the team is still
 * forming" had nowhere to live, and a game could not be recorded until somebody had been
 * named to it.
 *
 * A visitor never sees one of these on its own. A game is public in a season once a team plays
 * it; this is what the board sees and the visitor does not, and it doubles as the board's list
 * of what is left to do this season.
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
