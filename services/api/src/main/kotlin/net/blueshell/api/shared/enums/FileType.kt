package net.blueshell.api.shared.enums

import io.swagger.v3.oas.annotations.media.Schema

private const val MB = 1024L * 1024L

/** What a browser sends for the picture formats these pages draw. */
private val IMAGE = setOf("image/png", "image/jpeg", "image/jpg", "image/webp")

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
) {
    DOCUMENT("documents"),
    PROFILE_PICTURE("profile-pictures"),
    EVENT_BANNER("event-banners"),
    EVENT_PICTURE("event-pictures"),
    SPONSOR_PICTURE("sponsor-pictures"),

    /** A team's own poster, uploaded by an admin rather than bundled into the frontend. */
    TEAM_POSTER("team-posters", publiclyReadable = true, maxBytes = 15 * MB, allowedMediaTypes = IMAGE),

    /** The image behind an esports page, set for a game, a season, a team, or a combination. */
    ESPORTS_BANNER("esports-banners", publiclyReadable = true, maxBytes = 15 * MB, allowedMediaTypes = IMAGE),

    /** A player's picture on one roster, which may differ from the account's own portrait. */
    ROSTER_ICON("roster-icons", publiclyReadable = true, maxBytes = 5 * MB, allowedMediaTypes = IMAGE),
    ;
}
