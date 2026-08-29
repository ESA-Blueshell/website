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
 *
 * [path] is here because a picture is uploaded on its own and put on a record by the save that
 * names it, so a picker that is showing an image has to be able to say which one it is holding.
 * It is the same fact as [url] with the route taken off, rather than anything new.
 */
@Schema(name = "Image", description = "An image a public page draws, and the widths it is stored at")
data class Image(
    @Schema(description = "Where the full-size image is served")
    val url: String,
    @Schema(description = "Where it is stored, which is what a save points at to put it on a record")
    val path: String,
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
 * The widths come from the copies stored against this picture, narrowest first. A picture
 * uploaded before the ladder existed, or one whose kind lists no widths, carries none — and a
 * caller that finds none draws the full-size image, which is what it did before.
 */
fun File.asImage(): Image = Image(
    url = PublicFileUrls.of(this),
    path = path,
    width = width,
    height = height,
    renditions = renditions.mapNotNull { copy ->
        copy.renditionWidth?.let { ImageRendition(url = PublicFileUrls.of(copy), width = it) }
    },
)
