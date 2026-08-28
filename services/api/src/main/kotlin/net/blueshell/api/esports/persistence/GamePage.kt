package net.blueshell.api.esports.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

/**
 * A game the association plays: what it is called, the art it is drawn with, the address its
 * page answers to, what that page says, where it sits among the others, and whether a team is
 * still fielded in it.
 *
 * Everything that makes a game itself is here. The name was a label on a compiled enum and the
 * accent, the mark and the banner were written into the frontend, so a game could not be added
 * without a deploy however complete its row was.
 */
@Entity
@Table(
    name = "game_page",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_game_page_code", columnNames = ["game"]),
        UniqueConstraint(name = "uk_game_page_slug", columnNames = ["slug", "deleted_at"]),
    ],
    indexes = [Index(name = "idx_game_page_deleted_at", columnList = "deleted_at")],
)
@SQLDelete(sql = "UPDATE game_page SET deleted_at = NOW(6), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class GamePage(
    /** What everything else points at. A game's identity, and not editable. */
    @Column(name = "game", nullable = false, length = 32)
    var game: String,

    /** What the pages print. Free to change; the code it belongs to is not. */
    @Column(name = "name", nullable = false, length = 64)
    var name: String,

    /** The address the page answers to. What the router already used, so links keep working. */
    @Column(name = "slug", nullable = false, length = 64)
    var slug: String,

    @Lob
    @Column(name = "intro")
    var intro: String? = null,

    /** The colour that carries this game across the island, where one has been chosen. */
    @Column(name = "accent", length = 32)
    var accent: String? = null,

    /** The game's own mark, as an asset file name, where the association has one. */
    @Column(name = "mark", length = 255)
    var mark: String? = null,

    /** The image behind the game on the index, as an asset file name. */
    @Column(name = "banner", length = 255)
    var banner: String? = null,

    @Column(name = "sort_index", nullable = false)
    var sortIndex: Int = 0,

    /** Whether the association still fields a team in it. A retired game keeps its history. */
    @Column(name = "fielded", nullable = false)
    var fielded: Boolean = true,
) : AuditedAutoIdEntity()
