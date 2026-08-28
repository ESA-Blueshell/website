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
import net.blueshell.api.esports.domain.BannerLevel
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.enums.Game
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

/**
 * The image behind an esports page, and how narrowly it was set.
 *
 * A banner names a game always, and may narrow that to a season, a team, or both. One upload
 * restyles a whole game; a single team can still override it. Which one a page ends up with is
 * [net.blueshell.api.esports.domain.mostSpecificBanner]'s answer.
 *
 * The uniqueness of a combination is held by a generated `scope_key` column rather than by an
 * index over the two nullable ones, since MariaDB counts NULLs as distinct. The database writes
 * it and nothing here maps it.
 */
@Entity
@Table(
    name = "esports_banner",
    indexes = [
        Index(name = "idx_esports_banner_game", columnList = "game, deleted_at"),
        Index(name = "idx_esports_banner_deleted_at", columnList = "deleted_at"),
    ],
)
@SQLDelete(sql = "UPDATE esports_banner SET deleted_at = NOW(6), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class EsportsBanner(
    @Enumerated(EnumType.STRING)
    @Column(name = "game", nullable = false, length = 32)
    var game: Game,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    var file: File,

    /** The season this banner is for, or none when it carries every season the game played. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id")
    var season: Season? = null,

    /** The team this banner is for, or none when it carries every team in the game. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    var team: Team? = null,
) : AuditedAutoIdEntity(), BannerLevel {

    override val seasonId: Long?
        get() = season?.id

    override val teamId: Long?
        get() = team?.id
}
