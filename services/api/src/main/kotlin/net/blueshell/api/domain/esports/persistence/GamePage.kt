package net.blueshell.api.domain.esports.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.blueshell.api.shared.enums.Game
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

/**
 * How a game presents itself: its address, what is said about it, where it sits in the list,
 * and whether the association still fields a team in it.
 *
 * The enum remains what a team, a game account and the cohort rules refer to; this is only
 * presentation. Adding a game is still a code change, which is honest — a new game needs a
 * banner and an accent written for it either way.
 */
@Entity
@Table(
    name = "game_page",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_game_page_game", columnNames = ["game", "deleted_at"]),
        UniqueConstraint(name = "uk_game_page_slug", columnNames = ["slug", "deleted_at"]),
    ],
    indexes = [Index(name = "idx_game_page_deleted_at", columnList = "deleted_at")],
)
@SQLDelete(sql = "UPDATE game_page SET deleted_at = NOW(6), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class GamePage(
    @Enumerated(EnumType.STRING)
    @Column(name = "game", nullable = false, length = 32)
    var game: Game,

    /** The address the page answers to. What the router already used, so links keep working. */
    @Column(name = "slug", nullable = false, length = 64)
    var slug: String,

    @Lob
    @Column(name = "intro")
    var intro: String? = null,

    @Column(name = "sort_index", nullable = false)
    var sortIndex: Int = 0,

    /** Whether the association still fields a team in it. A retired game keeps its history. */
    @Column(name = "fielded", nullable = false)
    var fielded: Boolean = true,
) : AuditedAutoIdEntity()
