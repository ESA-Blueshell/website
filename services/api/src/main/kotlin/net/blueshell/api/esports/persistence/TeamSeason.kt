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
 * A team the association fielded in a game in a season.
 *
 * The game is here rather than on the team because a team is not a game's: the same team plays
 * different games, in the same season or across the years, with a different line-up in each. So
 * this is the row that says which game was played, and the roster hangs off it.
 *
 * Separate from the roster because the two are decided at different times: that a team is
 * being fielded is settled when the season is planned, and who plays for it is settled over
 * the weeks after. Inferring this from the roster meant a team could not exist until somebody
 * had been named to it.
 */
@Entity
@Table(
    name = "team_season",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_team_season",
            columnNames = ["team_id", "game", "season_id", "deleted_at"],
        ),
    ],
    indexes = [
        Index(name = "idx_team_season_game", columnList = "game, season_id, deleted_at"),
        Index(name = "idx_team_season_season", columnList = "season_id, deleted_at"),
        Index(name = "idx_team_season_deleted_at", columnList = "deleted_at"),
    ],
)
@SQLDelete(sql = "UPDATE team_season SET deleted_at = NOW(6), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class TeamSeason(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    var team: Team,

    @Column(name = "game", nullable = false, length = 32)
    var game: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    var season: Season,

    /**
     * The art this team is drawn with in this game's band, in this season.
     *
     * On the fielding rather than on the team: a team playing two games is drawn with each
     * game's own art, and BS HyperS keeps its CS:GO picture on the seasons it played CS:GO.
     * Fielding a team again carries the last one across, so it is only touched when it changes.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "banner_file_id")
    var banner: File? = null,
) : AuditedAutoIdEntity()
