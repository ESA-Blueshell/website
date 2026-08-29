package net.blueshell.api.file.api

import net.blueshell.api.file.persistence.File

/**
 * Where a publicly readable file is served from.
 *
 * One place builds this so the route and the payloads that point at it cannot drift apart.
 *
 * A path rather than an absolute url: the api cannot know what sits in front of it, and a
 * hardcoded host is one more thing to get wrong per environment. The frontend does NOT answer
 * on this origin — in development it runs on another port, and in production the api answers
 * under /api on the same host — so a caller must resolve this against the api before putting
 * it in an `src`. The frontend does that once, at its esports adapter.
 */
object PublicFileUrls {
    const val PATH = "/files/public"

    /** The route, whose two segments are the stored path this object splits and rejoins. */
    const val MAPPING = "$PATH/{directory}/{filename}"

    fun of(file: File): String = "$PATH/${file.path}"

    /** The stored path a request for [MAPPING] names. */
    fun pathOf(directory: String, filename: String): String = "$directory/$filename"
}
