package net.blueshell.api.file.api

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

    fun of(fileId: Long): String = "$PATH/$fileId"
}
