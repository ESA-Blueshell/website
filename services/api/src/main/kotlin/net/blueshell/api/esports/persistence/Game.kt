package net.blueshell.api.esports.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.model.AuditedAutoIdEntity

/**
 * A game the association plays: its name, art, address, blurb and place among the others.
 *
 * Whether it is still played is derived rather than stored — a game is current when a team
 * played it in this season or the one before — so there is only one source for that claim.
 *
 * Removal is real rather than soft, unlike everything else in this module: a game holding a team
 * cannot be removed at all, and its code is unique across every row, which a soft delete would
 * hold against a game added by mistake for good. `deleted_at` is vestigial, carrying the
 * sentinel on every row and scoping the slug index as a no-op. Nothing filters on it, and
 * nothing should start to.
 */
@Entity
@Table(
    name = "game",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_game_code", columnNames = ["code"]),
        UniqueConstraint(name = "uk_game_slug", columnNames = ["slug", "deleted_at"]),
    ],
    indexes = [Index(name = "idx_game_deleted_at", columnList = "deleted_at")],
)
class Game(
    /** The identifier teams, rosters and game accounts reference. Never changes. */
    @Column(name = "code", nullable = false, length = 32)
    var code: String,

    /** The name a reader sees. Editable, unlike [code]. */
    @Column(name = "name", nullable = false, length = 64)
    var name: String,

    /** The URL segment this game is served from, e.g. "counter-strike-2". */
    @Column(name = "slug", nullable = false, length = 64)
    var slug: String,

    @Lob
    @Column(name = "intro")
    var intro: String? = null,

    /** Hex colour used to accent this game across the site. Null falls back to the brand colour. */
    @Column(name = "accent", length = 32)
    var accent: String? = null,

    /**
     * The game's own image, drawn where the game is listed among the others.
     *
     * That listing is the only place either of a game's pictures is drawn; a game read on its
     * own carries the accent and nothing else.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "banner_file_id")
    var banner: File? = null,

    /**
     * The game's logo, drawn there beside the name.
     *
     * It was a filename naming a file bundled into the frontend, so the only logos a game could
     * carry were the ones a developer had shipped and a name matching nothing drew nothing while
     * saying so to nobody. It is an upload like every other picture here now.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "icon_file_id")
    var icon: File? = null,

    @Column(name = "sort_index", nullable = false)
    var sortIndex: Int = 0,

) : AuditedAutoIdEntity()
