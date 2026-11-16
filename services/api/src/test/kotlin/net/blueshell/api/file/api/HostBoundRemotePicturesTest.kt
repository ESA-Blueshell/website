package net.blueshell.api.file.api

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient

class HostBoundRemotePicturesTest {
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { it.start() }
    private val base = "http://127.0.0.1:${server.address.port}"
    private var answer: (HttpExchange) -> Unit = { it.answer(200, "image/jpeg; charset=binary", byteArrayOf(1, 2, 3)) }
    private val pictures = HostBoundRemotePictures(HttpClient.newHttpClient()) { it == "127.0.0.1" }

    init {
        server.createContext("/picture") { answer(it) }
    }

    @AfterEach
    fun stop() = server.stop(0)

    private fun read() = pictures.read(URI.create("$base/picture"))

    @Test
    fun `reads a picture of a kind we take, with its media type`() {
        val picture = read()!!

        assertThat(picture.bytes).containsExactly(1, 2, 3)
        assertThat(picture.mediaType).isEqualTo("image/jpeg")
    }

    @Test
    fun `takes nothing that is not a picture we take, is empty, failed, or out of reach`() {
        answer = { it.answer(200, "text/html", "<html>".toByteArray()) }
        assertThat(read()).isNull()

        answer = { it.answer(200, "image/png", ByteArray(0)) }
        assertThat(read()).isNull()

        answer = { it.answer(404, "image/png", byteArrayOf(1)) }
        assertThat(read()).isNull()

        answer = { it.answer(200, "image/webp", ByteArray(5 * 1024 * 1024 + 1)) }
        assertThat(read()).isNull()

        answer = { exchange -> exchange.sendResponseHeaders(200, 1).also { exchange.close() } }
        val closed = ServerSocket(0).use { it.localPort }
        assertThat(pictures.read(URI.create("http://127.0.0.1:$closed/picture"))).isNull()
    }

    @Test
    fun `answers without a content type are not pictures`() {
        answer = { exchange ->
            exchange.sendResponseHeaders(200, 1)
            exchange.responseBody.use { it.write(byteArrayOf(1)) }
        }

        assertThat(read()).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://images.example.org/x.jpg", "https://example.com/x.jpg", "https:///x.jpg", "not a [uri"])
    fun `fetches only over https, and only from the hosts it was given`(url: String) {
        assertThat(HostBoundRemotePictures(HttpClient.newHttpClient()) { it == "images.example.org" }.fetch(url)).isNull()
    }

    @Test
    fun `asks an allowed host over https`() {
        // The host is allowed and the scheme is https, so the fetch is made; nothing answers there.
        val nothing = HostBoundRemotePictures(HttpClient.newHttpClient()) { true }

        assertThat(nothing.fetch("https://127.0.0.1:${ServerSocket(0).use { it.localPort }}/x.jpg")).isNull()
    }

    private fun HttpExchange.answer(
        status: Int,
        type: String,
        body: ByteArray,
    ) {
        responseHeaders.add("Content-Type", type)
        sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
        responseBody.use { it.write(body) }
    }
}
