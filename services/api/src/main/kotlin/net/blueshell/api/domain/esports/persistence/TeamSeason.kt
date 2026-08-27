package net.blueshell.api.domain.esports.persistence

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
 * A team the association fielded in a season.
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
        UniqueConstraint(name = "uk_team_season", columnNames = ["team_id", "season_id", "deleted_at"]),
    ],
    indexes = [
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    var season: Season,
) : AuditedAutoIdEntity()
