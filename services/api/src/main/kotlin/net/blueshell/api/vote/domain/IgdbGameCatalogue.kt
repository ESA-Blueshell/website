package net.blueshell.api.vote.domain

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Looks games up in IGDB with a Twitch app token from the client-credentials flow. The token is
 * kept until shortly before it expires and fetched again once IGDB turns it away. IGDB allows four
 * requests a second per client, which a member typing behind the page's debounce stays under.
 */
class IgdbGameCatalogue(
    private val clientId: String,
    private val clientSecret: String,
    private val http: HttpClient,
    private val json: ObjectMapper,
    private val clock: Clock,
    private val tokenUrl: String = "https://id.twitch.tv/oauth2/token",
    private val apiUrl: String = "https://api.igdb.com/v4",
) : GameCatalogue {
    private data class Token(
        val value: String,
        val expiresAt: Instant,
    )

    @Volatile private var token: Token? = null

    override fun search(
        query: String,
        limit: Int,
        releasedIn: Int?,
    ): List<CatalogueGame> {
        val year = releasedIn?.let { " & first_release_date >= ${startOf(it)} & first_release_date < ${startOf(it + 1)}" }.orEmpty()
        return games("search \"${escape(query)}\"; fields $FIELDS; where version_parent = null$year; limit $limit;")
    }

    override fun byId(igdbId: Long): CatalogueGame? = games("fields $FIELDS; where id = $igdbId;").firstOrNull()

    private fun games(body: String): List<CatalogueGame> {
        val response = query(body, tokenNow()).let { if (it.statusCode() == UNAUTHORIZED) query(body, tokenNow(fresh = true)) else it }
        if (response.statusCode() !in OK_RANGE) throw GameSearchUnavailable()
        return json.readTree(response.body()).mapNotNull(::gameOf)
    }

    private fun query(
        body: String,
        bearer: String,
    ): HttpResponse<String> =
        send(
            HttpRequest
                .newBuilder(URI.create("$apiUrl/games"))
                .timeout(TIMEOUT)
                .header("Client-ID", clientId)
                .header("Authorization", "Bearer $bearer")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
        )

    private fun tokenNow(fresh: Boolean = false): String {
        val held = token
        if (!fresh && held != null && clock.instant().isBefore(held.expiresAt)) return held.value
        val form =
            mapOf("client_id" to clientId, "client_secret" to clientSecret, "grant_type" to "client_credentials")
                .entries
                .joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, StandardCharsets.UTF_8)}" }
        val response =
            send(
                HttpRequest
                    .newBuilder(URI.create(tokenUrl))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build(),
            )
        if (response.statusCode() !in OK_RANGE) throw GameSearchUnavailable()
        val answer = json.readTree(response.body())
        val value = answer.get("access_token")?.asString() ?: throw GameSearchUnavailable()
        val lifetime = answer.get("expires_in")?.asLong() ?: 0
        // Renew a minute early, so a token never lapses between reading it and IGDB checking it.
        return value.also { token = Token(it, clock.instant().plusSeconds(lifetime - EARLY_RENEWAL_SECONDS)) }
    }

    private fun send(request: HttpRequest): HttpResponse<String> =
        try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (_: IOException) {
            throw GameSearchUnavailable()
        }

    private fun gameOf(node: JsonNode): CatalogueGame? {
        val id = node.get("id")?.asLong() ?: return null
        val name = node.get("name")?.asString()?.takeIf { it.isNotBlank() } ?: return null
        val released = node.get("first_release_date")?.asLong()
        return CatalogueGame(
            igdbId = id,
            name = name,
            coverImageId = node.get("cover")?.get("image_id")?.asString(),
            firstReleaseYear = released?.let { Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).year },
        )
    }

    private companion object {
        const val FIELDS = "name,first_release_date,cover.image_id"
        const val UNAUTHORIZED = 401
        const val EARLY_RENEWAL_SECONDS = 60L
        val OK_RANGE = 200..299
        val TIMEOUT: Duration = Duration.ofSeconds(5)

        // IGDB's query language quotes a search with double quotes and escapes with a backslash.
        fun escape(query: String) = query.replace("\\", "\\\\").replace("\"", "\\\"")

        fun startOf(year: Int) = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
    }
}
