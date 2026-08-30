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
 * A game the association plays: what it is called, the art it is drawn with, the address its
 * page answers to, what that page says, where it sits among the others, and whether a team is
 * still fielded in it.
 *
 * Everything that makes a game itself is here. The name was a label on a compiled enum and the
 * accent, the icon and the banner were written into the frontend, so a game could not be added
 * without a deploy however complete its row was.
 *
 * Whether the association still plays it is not written down here. It was a flag somebody set,
 * beside season records that answered the same question more precisely, and two sources of one
 * claim drift. It is derived instead: a game is current when a team played it in the season we
 * are in or the one before it.
 *
 * Removal is real rather than soft, unlike everything else on these pages. A game holding a team
 * cannot be removed at all, so the only game that ever goes is one that holds nothing and has
 * none to keep. Its code is also
 * unique across every row, since that is what a team and a game account point at, and a soft
 * delete would hold that code against a game added by mistake for good.
 *
 * The `deleted_at` column is left over from when this was soft-deleted and is now vestigial: it
 * is the sentinel on every row, because no way to delete a game existed before removal became
 * real. It still scopes the slug's unique index, where it is therefore a no-op. Nothing filters
 * on it and nothing should start to.
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
class GamePage(
    /** The identifier teams, rosters and game accounts reference. Never changes. */
    @Column(name = "game", nullable = false, length = 32)
    var game: String,

    /** The name shown on the pages. Editable, unlike [game]. */
    @Column(name = "name", nullable = false, length = 64)
    var name: String,

    /** The URL segment this game's page is served from, e.g. "counter-strike-2". */
    @Column(name = "slug", nullable = false, length = 64)
    var slug: String,

    @Lob
    @Column(name = "intro")
    var intro: String? = null,

    /** Hex colour used to accent this game across the site. Null falls back to the brand colour. */
    @Column(name = "accent", length = 32)
    var accent: String? = null,

    /**
     * The game's own image, drawn in the slice for it on the esports index.
     *
     * The only place either of a game's pictures is drawn is a slice: the index header and the
     * game's own page are the accent and nothing else.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "banner_file_id")
    var banner: File? = null,

    /**
     * The game's logo, drawn in that same slice beside the name.
     *
     * It was a filename naming a file bundled into the frontend, so the only logos a game could
     * carry were the ones a developer had shipped and a name matching nothing drew nothing while
     * saying so to nobody. It is an upload like every other picture on these pages now.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "icon_file_id")
    var icon: File? = null,

    @Column(name = "sort_index", nullable = false)
    var sortIndex: Int = 0,

) : AuditedAutoIdEntity()
