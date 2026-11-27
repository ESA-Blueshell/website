package net.blueshell.api.vote.domain

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import net.blueshell.api.vote.persistence.TrackSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.net.ServerSocket

class OEmbedTrackPreviewsTest {
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { it.start() }
    private val base = "http://127.0.0.1:${server.address.port}"
    private var asked: String? = null
    private var answer: (HttpExchange) -> Unit = {
        it.answer(200, """{"title":"Resurrections","thumbnail_url":"https://i.ytimg.com/vi/x/hq.jpg"}""")
    }

    init {
        server.createContext("/oembed") { exchange ->
            asked = exchange.requestURI.rawQuery
            answer(exchange)
        }
    }

    @AfterEach
    fun stop() = server.stop(0)

    private fun previews(endpoint: String = "$base/oembed") =
        OEmbedTrackPreviews(
            RestClient.create(),
            JsonMapper.builder().build(),
            mapOf(
                TrackSource.YOUTUBE to endpoint,
                TrackSource.SPOTIFY to endpoint,
            ),
        )

    private val video = TrackLink(TrackSource.YOUTUBE, "dQw4w9WgXcQ")

    @Test
    fun `asks the link's service about the link's address, and reads its title and thumbnail`() {
        assertThat(previews().of(video)).isEqualTo(TrackPreview("Resurrections", "https://i.ytimg.com/vi/x/hq.jpg"))
        assertThat(asked).isEqualTo("format=json&url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3DdQw4w9WgXcQ")
    }

    @Test
    fun `a description without a thumbnail still reads`() {
        answer = { it.answer(200, """{"title":"Resurrections"}""") }

        assertThat(previews().of(video)).isEqualTo(TrackPreview("Resurrections", null))
    }

    @Test
    fun `a private or removed link, an unreadable answer, or one without a title is no preview`() {
        answer = { it.answer(404, "Not Found") }
        assertThat(previews().of(video)).isNull()

        answer = { it.answer(200, "not json") }
        assertThat(previews().of(video)).isNull()

        answer = { it.answer(200, """{"title":"  "}""") }
        assertThat(previews().of(video)).isNull()
    }

    @Test
    fun `a service out of reach is no preview`() {
        val closed = ServerSocket(0).use { it.localPort }

        assertThat(previews("http://127.0.0.1:$closed/oembed").of(video)).isNull()
    }

    @Test
    fun `asks YouTube's and Spotify's own endpoints by default`() {
        assertThat(OEmbedTrackPreviews(RestClient.create(), JsonMapper.builder().build())).isNotNull
    }

    private fun HttpExchange.answer(
        status: Int,
        body: String,
    ) {
        val bytes = body.toByteArray()
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
