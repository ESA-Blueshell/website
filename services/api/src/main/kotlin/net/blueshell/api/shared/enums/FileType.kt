package net.blueshell.api.shared.enums

import io.swagger.v3.oas.annotations.media.Schema

private const val MB = 1024L * 1024L

/** What a browser sends for the picture formats these pages draw. */
private val IMAGE = setOf("image/png", "image/jpeg", "image/jpg", "image/webp")
private val LARGE_PUBLIC_IMAGE_WIDTHS = listOf(320, 640, 960, 1280, 1920, 2560)
private val ICON_WIDTHS = listOf(128, 256, 512)

/** A player's icon is drawn at about 72px and never larger, so it is capped a step lower. */
private val PLAYER_ICON_WIDTHS = listOf(128, 256)

/**
 * A portrait is one face, drawn small and enlarged once.
 *
 * A board's seat shows a thumbnail of about 96px beside the name and opens to a picture of
 * about 320px, so 160 covers the thumbnail, 320 covers it on a dense display and covers the
 * opened picture, and 640 covers the opened picture on a dense display. Nothing draws one
 * wider, so the ladder stops there — the same argument [FileType.ROSTER_ICON] makes for
 * capping below the other icons, one rung higher because a portrait is a photograph of a
 * person rather than a logo and it does get opened.
 */
private val PORTRAIT_WIDTHS = listOf(160, 320, 640)

/**
 * What an uploaded file is, which decides where it is stored and who may read it back.
 *
 * [publiclyReadable] is opt-in per kind rather than a property of a single file: these are the
 * kinds that exist to be drawn on a page anybody can visit. Everything else stays behind the
 * endpoint that knows who may read it.
 */
@Schema(enumAsRef = true)
enum class FileType(
    val directory: String,
    val publiclyReadable: Boolean = false,
    /** The largest upload of this kind, or null where the kind sets no limit of its own. */
    val maxBytes: Long? = null,
    /** The content types this kind admits, or empty where the kind admits anything. */
    val allowedMediaTypes: Set<String> = emptySet(),
    /** The largest edge stored for public page images, or null when bytes are stored as sent. */
    val maxImageEdge: Int? = null,
    /** WebP quality used for lossy public page images. */
    val webpQuality: Int? = null,
    /** Whether this image kind is encoded as lossless WebP. */
    val webpLossless: Boolean = false,
    /** Candidate rendition widths for this kind, recorded with the ceiling that governs them. */
    val renditionWidths: List<Int> = emptyList(),
) {
    DOCUMENT("documents"),
    PROFILE_PICTURE("profile-pictures"),
    EVENT_BANNER("event-banners"),
    EVENT_PICTURE("event-pictures"),
    SPONSOR_PICTURE("sponsor-pictures"),

    /** A game's own image, drawn where the game is listed among the others. */
    GAME_BANNER(
        "game-banners",
        publiclyReadable = true,
        maxBytes = 15 * MB,
        allowedMediaTypes = IMAGE,
        maxImageEdge = 2560,
        webpQuality = 82,
        renditionWidths = LARGE_PUBLIC_IMAGE_WIDTHS,
    ),

    /** A game's logo, drawn beside its name. Lossless keeps flat colour and alpha edges sharp. */
    GAME_ICON(
        "game-icons",
        publiclyReadable = true,
        maxBytes = 5 * MB,
        allowedMediaTypes = IMAGE,
        maxImageEdge = 512,
        webpLossless = true,
        renditionWidths = ICON_WIDTHS,
    ),

    /** A team's own banner, drawn where the team is listed under its game. */
    TEAM_BANNER(
        "team-banners",
        publiclyReadable = true,
        maxBytes = 15 * MB,
        allowedMediaTypes = IMAGE,
        maxImageEdge = 2560,
        webpQuality = 82,
        renditionWidths = LARGE_PUBLIC_IMAGE_WIDTHS,
    ),

    /** A team's own logo, drawn beside its name. Lossless, for the same reason a game's is. */
    TEAM_ICON(
        "team-icons",
        publiclyReadable = true,
        maxBytes = 5 * MB,
        allowedMediaTypes = IMAGE,
        maxImageEdge = 512,
        webpLossless = true,
        renditionWidths = ICON_WIDTHS,
    ),

    /**
     * A player's picture on one roster, which may differ from the account's own portrait.
     *
     * Capped a step below the other icons, at the size a player's picture is shown at in the
     * games these rosters are for: nothing on the site draws one above about 72px, so a 512
     * copy was bytes nobody ever fetched.
     */
    ROSTER_ICON(
        "roster-icons",
        publiclyReadable = true,
        maxBytes = 5 * MB,
        allowedMediaTypes = IMAGE,
        maxImageEdge = 256,
        webpQuality = 85,
        renditionWidths = PLAYER_ICON_WIDTHS,
    ),

    /** A board's group photograph, drawn full-bleed across a band. A game banner's twin. */
    BOARD_PHOTO(
        "board-photos",
        publiclyReadable = true,
        maxBytes = 15 * MB,
        allowedMediaTypes = IMAGE,
        maxImageEdge = 2560,
        webpQuality = 82,
        renditionWidths = LARGE_PUBLIC_IMAGE_WIDTHS,
    ),

    /**
     * One seat's portrait, drawn beside the name and enlarged when the seat opens.
     *
     * The ceiling is on the longest edge, and a portrait is taller than it is wide, so 960
     * there is about 640 across — the widest width [PORTRAIT_WIDTHS] lists. Lossy at 85 rather
     * than 82, because a face at 160px shows what a group photograph at 1920px hides. Sized
     * like a photograph rather than like a logo: what arrives is a phone camera's output, and
     * the ceiling is applied after it is admitted rather than instead of admitting it.
     */
    BOARD_PORTRAIT(
        "board-portraits",
        publiclyReadable = true,
        maxBytes = 15 * MB,
        allowedMediaTypes = IMAGE,
        maxImageEdge = 960,
        webpQuality = 85,
        renditionWidths = PORTRAIT_WIDTHS,
    ),
    ;
}
