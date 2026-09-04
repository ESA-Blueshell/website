package net.blueshell.api.file.api

import net.blueshell.api.file.persistence.File

/**
 * Where a publicly readable file is served from.
 *
 * Built in one place, so the route and the payloads pointing at it cannot drift apart. A path
 * rather than an absolute url, since the api cannot know what sits in front of it — which means
 * a caller must resolve it against the api before putting it in an `src`, because the frontend
 * does not answer on this origin. The frontend does that once, at its esports adapter.
 */
object PublicFileUrls {
    const val PATH = "/files/public"

    /** The route, whose two segments are the stored path this object splits and rejoins. */
    const val MAPPING = "$PATH/{directory}/{filename}"

    /** Where a picture meant to be seen is uploaded. One endpoint, whatever it ends up on. */
    const val UPLOAD = "/files/images"

    fun of(file: File): String = "$PATH/${file.path}"

    /** The stored path a request for [MAPPING] names. */
    fun pathOf(directory: String, filename: String): String = "$directory/$filename"
}
