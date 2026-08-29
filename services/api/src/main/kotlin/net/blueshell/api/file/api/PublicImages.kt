package net.blueshell.api.file.api

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.file.persistence.File

/** One stored width of an image, and where that width is served. */
@Schema(name = "ImageRendition", description = "One stored width of an image")
data class ImageRendition(
    @Schema(description = "Where this width is served")
    val url: String,
    val width: Int,
)

/**
 * An image a public page draws.
 *
 * Carries where it is served, how large it is, and the widths it is stored at. The dimensions
 * let a caller reserve its space so a page does not shift as it loads; the renditions let a
 * caller ask for the width it actually needs.
 *
 * Widths and urls rather than a finished `srcset`: composing that string is a display decision
 * and belongs where the markup is written, not in the payload.
 */
@Schema(name = "Image", description = "An image a public page draws, and the widths it is stored at")
data class Image(
    @Schema(description = "Where the full-size image is served")
    val url: String,
    @Schema(description = "How wide it is, absent where its size could not be read")
    val width: Int? = null,
    @Schema(description = "How tall it is, absent where its size could not be read")
    val height: Int? = null,
    @Schema(description = "The widths it is stored at, narrowest first")
    val renditions: List<ImageRendition> = emptyList(),
)

/**
 * The image a stored file is, for a payload to point a page at.
 *
 * The rendition list is empty: a file is stored at one width for now, and the ladder that
 * fills this in arrives with the work that derives it.
 */
fun File.asImage(): Image = Image(
    url = PublicFileUrls.of(this),
    width = width,
    height = height,
)
