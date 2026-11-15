package net.blueshell.api.vote.domain

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.http.HttpClient
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class IgdbGameCatalogueTest {
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { it.start() }
    private val base = "http://127.0.0.1:${server.address.port}"
    private var now: Instant = Instant.parse("2026-11-20T18:00:00Z")
    private val clock =
        object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC

            override fun withZone(zone: ZoneId?): Clock = this

            override fun instant(): Instant = now
        }

    private val tokensIssued = mutableListOf<String>()
    private val queries = mutableListOf<Pair<String, String>>()
    private var tokenAnswer: (HttpExchange) -> Unit = { exchange ->
        val issued = "token${tokensIssued.size + 1}"
        tokensIssued += exchange.requestBody.readAllBytes().decodeToString()
        exchange.answer(200, """{"access_token":"$issued","expires_in":3600,"token_type":"bearer"}""")
    }
    private var gamesAnswer: (HttpExchange) -> Unit = { it.answer(200, CELESTE) }

    init {
        server.createContext("/token") { tokenAnswer(it) }
        server.createContext("/v4/games") { exchange ->
            queries += exchange.requestHeaders.getFirst("Authorization") to exchange.requestBody.readAllBytes().decodeToString()
            check(exchange.requestHeaders.getFirst("Client-ID") == "client")
            gamesAnswer(exchange)
        }
    }

    @AfterEach
    fun stop() = server.stop(0)

    private fun catalogue(api: String = "$base/v4") =
        IgdbGameCatalogue("client", "secret", HttpClient.newHttpClient(), JsonMapper.builder().build(), clock, "$base/token", api)

    @Test
    fun `a search asks for main games by name, with a token from the client-credentials flow`() {
        val found = catalogue().search("Cel\"este", 10, null)

        assertThat(found).containsExactly(CatalogueGame(26226, "Celeste", "co3byy", 2018))
        assertThat(tokensIssued.single()).isEqualTo("client_id=client&client_secret=secret&grant_type=client_credentials")
        assertThat(queries.single().first).isEqualTo("Bearer token1")
        assertThat(queries.single().second).isEqualTo(
            "search \"Cel\\\"este\"; fields name,first_release_date,cover.image_id; where version_parent = null; limit 10;",
        )
    }

    @Test
    fun `a search for one year's releases asks for that year's first release dates`() {
        catalogue().search("a", 5, releasedIn = 2026)

        assertThat(queries.single().second).isEqualTo(
            "search \"a\"; fields name,first_release_date,cover.image_id; " +
                "where version_parent = null & first_release_date >= 1767225600 & first_release_date < 1798761600; limit 5;",
        )
    }

    @Test
    fun `a game is read by its id, and none is null`() {
        val igdb = catalogue()

        assertThat(igdb.byId(26226)?.name).isEqualTo("Celeste")
        assertThat(queries.single().second).isEqualTo("fields name,first_release_date,cover.image_id; where id = 26226;")

        gamesAnswer = { it.answer(200, "[]") }
        assertThat(igdb.byId(1)).isNull()
    }

    @Test
    fun `a token is kept until a minute before it expires`() {
        val igdb = catalogue()
        igdb.search("a", 1, null)
        now = now.plusSeconds(3600 - 61)
        igdb.search("a", 1, null)
        now = now.plusSeconds(1)
        igdb.search("a", 1, null)

        assertThat(queries.map { it.first }).containsExactly("Bearer token1", "Bearer token1", "Bearer token2")
    }

    @Test
    fun `a token IGDB turns away is fetched again, once`() {
        gamesAnswer = { exchange -> if (queries.size == 1) exchange.answer(401, "{}") else exchange.answer(200, CELESTE) }

        assertThat(catalogue().search("a", 1, null)).hasSize(1)
        assertThat(queries.map { it.first }).containsExactly("Bearer token1", "Bearer token2")
    }

    @Test
    fun `games without an id or a name are left out, and missing details stay empty`() {
        gamesAnswer = { it.answer(200, """[{"name":"No id"},{"id":2},{"id":3,"name":" "},{"id":4,"name":"Bare"}]""") }

        assertThat(catalogue().search("a", 5, null)).containsExactly(CatalogueGame(4, "Bare", null, null))
    }

    @Test
    fun `an error from IGDB makes the search unavailable`() {
        gamesAnswer = { it.answer(500, "{}") }

        assertThatThrownBy { catalogue().search("a", 1, null) }.isInstanceOf(GameSearchUnavailable::class.java)
    }

    @Test
    fun `a token Twitch refuses, or answers without, makes the search unavailable`() {
        tokenAnswer = { it.answer(400, "{}") }
        assertThatThrownBy { catalogue().search("a", 1, null) }.isInstanceOf(GameSearchUnavailable::class.java)

        tokenAnswer = { it.answer(200, """{"expires_in":3600}""") }
        assertThatThrownBy { catalogue().search("a", 1, null) }.isInstanceOf(GameSearchUnavailable::class.java)
    }

    @Test
    fun `a token answer without a lifetime is fetched again on the next search`() {
        tokenAnswer = { exchange ->
            tokensIssued += "issued"
            exchange.answer(200, """{"access_token":"token${tokensIssued.size}"}""")
        }
        val igdb = catalogue()
        igdb.search("a", 1, null)
        igdb.search("a", 1, null)

        assertThat(tokensIssued).hasSize(2)
    }

    @Test
    fun `IGDB out of reach makes the search unavailable`() {
        val closed = ServerSocket(0).use { it.localPort }

        assertThatThrownBy { catalogue("http://127.0.0.1:$closed/v4").search("a", 1, null) }.isInstanceOf(GameSearchUnavailable::class.java)
    }

    private fun HttpExchange.answer(
        status: Int,
        body: String,
    ) {
        val bytes = body.toByteArray()
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private companion object {
        const val CELESTE = """[{"id":26226,"name":"Celeste","first_release_date":1516579200,"cover":{"id":1,"image_id":"co3byy"}}]"""
    }
}
