package net.blueshell.api.shared.enums

import io.swagger.v3.oas.annotations.media.Schema

private const val MB = 1024L * 1024L

/** What a browser sends for the picture formats these pages draw. */
private val IMAGE = setOf("image/png", "image/jpeg", "image/jpg", "image/webp")
private val LARGE_PUBLIC_IMAGE_WIDTHS = listOf(320, 640, 960, 1280, 1920, 2560)
private val ICON_WIDTHS = listOf(128, 256, 512)

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

    /** A game's own image, drawn in the slice for it on the esports index. */
    GAME_BANNER(
        "game-banners",
        publiclyReadable = true,
        maxBytes = 15 * MB,
        allowedMediaTypes = IMAGE,
        maxImageEdge = 2560,
        webpQuality = 82,
        renditionWidths = LARGE_PUBLIC_IMAGE_WIDTHS,
    ),

    /** A game's logo, drawn in its slice beside the name. Lossless keeps flat colour and alpha edges sharp. */
    GAME_ICON(
        "game-icons",
        publiclyReadable = true,
        maxBytes = 5 * MB,
        allowedMediaTypes = IMAGE,
        maxImageEdge = 512,
        webpLossless = true,
        renditionWidths = ICON_WIDTHS,
    ),

    /** A team's own banner, drawn in the slice for it on its game's page. */
    TEAM_BANNER(
        "team-banners",
        publiclyReadable = true,
        maxBytes = 15 * MB,
        allowedMediaTypes = IMAGE,
        maxImageEdge = 2560,
        webpQuality = 82,
        renditionWidths = LARGE_PUBLIC_IMAGE_WIDTHS,
    ),

    /** A team's own logo, drawn in its slice beside the name. Lossless, for the same reason a game's is. */
    TEAM_ICON(
        "team-icons",
        publiclyReadable = true,
        maxBytes = 5 * MB,
        allowedMediaTypes = IMAGE,
        maxImageEdge = 512,
        webpLossless = true,
        renditionWidths = ICON_WIDTHS,
    ),

    /** A player's picture on one roster, which may differ from the account's own portrait. */
    ROSTER_ICON(
        "roster-icons",
        publiclyReadable = true,
        maxBytes = 5 * MB,
        allowedMediaTypes = IMAGE,
        maxImageEdge = 512,
        webpQuality = 85,
        renditionWidths = ICON_WIDTHS,
    ),
    ;
}
