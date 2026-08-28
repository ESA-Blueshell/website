package net.blueshell.api.file.api

/**
 * Where a publicly readable file is served from.
 *
 * One place builds this so the route and the payloads that point at it cannot drift apart.
 * Relative rather than absolute: the frontend and the api answer on the same origin, and a
 * hardcoded host is one more thing to get wrong per environment.
 */
object PublicFileUrls {
    const val PATH = "/files/public"

    fun of(fileId: Long): String = "$PATH/$fileId"
}
