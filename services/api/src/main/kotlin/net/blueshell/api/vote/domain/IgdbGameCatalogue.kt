package net.blueshell.api.vote.domain

import net.blueshell.api.shared.credentials.RotatingSecret
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Looks games up in IGDB with a Twitch app token from the client-credentials flow. The credentials
 * are read on each token request, so rotated credentials take over without a restart (api ADR-033). A
 * token is kept until shortly before it expires, until IGDB turns it away, or until the credentials
 * it was issued for change. [ThrottledGameCatalogue] keeps the lookups under IGDB's rate limit.
 */
class IgdbGameCatalogue(
    private val clientId: RotatingSecret,
    private val clientSecret: RotatingSecret,
    private val rest: RestClient,
    private val json: ObjectMapper,
    private val clock: Clock,
    private val tokenUrl: String = "https://id.twitch.tv/oauth2/token",
    private val apiUrl: String = "https://api.igdb.com/v4",
) : GameCatalogue {
    // Not a data class: its generated toString would print the secret.
    private class TwitchApp(
        val id: String,
        val secret: String,
    ) {
        override fun equals(other: Any?) = other is TwitchApp && other.id == id && other.secret == secret

        override fun hashCode() = 31 * id.hashCode() + secret.hashCode()

        override fun toString() = "TwitchApp($id)"
    }

    private data class Token(
        val value: String,
        val expiresAt: Instant,
        val issuedFor: TwitchApp,
    )

    private class Raw(
        val status: Int,
        val body: String,
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

    override fun coverUrl(
        imageId: String,
        size: CoverSize,
    ): String? {
        if (!IMAGE_ID.matches(imageId)) return null
        val token = if (size == CoverSize.SMALL) "t_cover_small" else "t_cover_big"
        return "https://images.igdb.com/igdb/image/upload/$token/$imageId.jpg"
    }

    private fun games(body: String): List<CatalogueGame> {
        var (app, bearer) = authorised()
        var answer = query(body, app, bearer)
        if (answer.status == UNAUTHORIZED) {
            authorised(fresh = true).let { (again, token) -> answer = query(body, again, token) }
        }
        if (answer.status !in OK_RANGE) throw GameSearchUnavailable()
        return json.readTree(answer.body).mapNotNull(::gameOf)
    }

    private fun currentApp() = TwitchApp(clientId.current(), clientSecret.current())

    // A rotation writes the id and the secret one at a time, so a refused pair is read once more.
    private fun authorised(fresh: Boolean = false): Pair<TwitchApp, String> {
        val app = currentApp()
        return try {
            app to tokenFor(app, fresh)
        } catch (refused: GameSearchUnavailable) {
            val again = currentApp()
            if (again == app) throw refused
            again to tokenFor(again, fresh = true)
        }
    }

    private fun query(
        body: String,
        app: TwitchApp,
        bearer: String,
    ): Raw =
        send {
            rest
                .post()
                .uri("$apiUrl/games")
                .header("Client-ID", app.id)
                .header("Authorization", "Bearer $bearer")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(body)
        }

    private fun tokenFor(
        app: TwitchApp,
        fresh: Boolean = false,
    ): String {
        val held = token?.takeIf { !fresh && it.issuedFor == app }
        if (held != null && clock.instant().isBefore(held.expiresAt)) return held.value
        val form =
            LinkedMultiValueMap<String, String>().apply {
                add("client_id", app.id)
                add("client_secret", app.secret)
                add("grant_type", "client_credentials")
            }
        val answer =
            send {
                rest
                    .post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
            }
        if (answer.status !in OK_RANGE) throw GameSearchUnavailable()
        val read = json.readTree(answer.body)
        val value = read.get("access_token")?.asString() ?: throw GameSearchUnavailable()
        val lifetime = read.get("expires_in")?.asLong() ?: 0
        // Renew a minute early, so a token never lapses between reading it and IGDB checking it.
        return value.also { token = Token(it, clock.instant().plusSeconds(lifetime - EARLY_RENEWAL_SECONDS), app) }
    }

    private fun send(request: () -> RestClient.RequestBodySpec): Raw =
        try {
            request().exchange { _, response -> Raw(response.statusCode.value(), response.bodyTo(String::class.java).orEmpty()) }
        } catch (_: RestClientException) {
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
        val IMAGE_ID = Regex("^[a-z0-9]{1,40}$")
        const val UNAUTHORIZED = 401
        const val EARLY_RENEWAL_SECONDS = 60L
        val OK_RANGE = 200..299

        // IGDB's query language quotes a search with double quotes and escapes with a backslash.
        fun escape(query: String) = query.replace("\\", "\\\\").replace("\"", "\\\"")

        fun startOf(year: Int) = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
    }
}
