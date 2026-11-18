package net.blueshell.api.vote.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class HostBoundRemotePicturesTest {
    private val http = mock<HttpClient>()
    private val pictures = HostBoundRemotePictures(http) { it == "images.igdb.com" }
    private val url = "https://images.igdb.com/igdb/image/upload/t_cover_small/co1.jpg"
    private val asked = mutableListOf<HttpRequest>()

    private fun answer(
        status: Int,
        body: ByteArray,
        vararg headers: Pair<String, String>,
    ) {
        val response =
            mock<HttpResponse<InputStream>> {
                on { statusCode() } doReturn status
                on { headers() } doReturn HttpHeaders.of(headers.groupBy({ it.first }, { it.second })) { _, _ -> true }
                on { body() } doReturn ByteArrayInputStream(body)
            }
        whenever(http.send(any(), any<HttpResponse.BodyHandler<InputStream>>())) doAnswer {
            asked += it.getArgument<HttpRequest>(0)
            response
        }
    }

    @Test
    fun `fetches a picture of a kind we take from an allowed host, with its media type`() {
        answer(200, byteArrayOf(1, 2, 3), "Content-Type" to "image/jpeg; charset=binary")

        val picture = pictures.fetch(url)!!

        assertThat(picture.bytes).containsExactly(1, 2, 3)
        assertThat(picture.mediaType).isEqualTo("image/jpeg")
        assertThat(asked.single().uri().toString()).isEqualTo(url)
    }

    @Test
    fun `takes nothing that is not a picture we take, is empty, failed, or has no type`() {
        answer(200, "<html>".toByteArray(), "Content-Type" to "text/html")
        assertThat(pictures.fetch(url)).isNull()

        answer(200, ByteArray(0), "Content-Type" to "image/png")
        assertThat(pictures.fetch(url)).isNull()

        answer(404, byteArrayOf(1), "Content-Type" to "image/png")
        assertThat(pictures.fetch(url)).isNull()

        answer(200, byteArrayOf(1))
        assertThat(pictures.fetch(url)).isNull()
    }

    @Test
    fun `refuses a picture past the limit, whether it says so up front or only by its length`() {
        val limit = 5 * 1024 * 1024
        answer(200, byteArrayOf(1), "Content-Type" to "image/webp", "Content-Length" to "${limit + 1}")
        assertThat(pictures.fetch(url)).isNull()

        answer(200, ByteArray(limit + 1), "Content-Type" to "image/webp")
        assertThat(pictures.fetch(url)).isNull()

        answer(200, ByteArray(limit), "Content-Type" to "image/webp")
        assertThat(pictures.fetch(url)!!.bytes).hasSize(limit)
    }

    @Test
    fun `a host out of reach is no picture`() {
        whenever(http.send(any(), any<HttpResponse.BodyHandler<InputStream>>())).thenThrow(IOException("refused"))

        assertThat(pictures.fetch(url)).isNull()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "http://images.igdb.com/x.jpg",
            "https://images.igdb.com:8443/x.jpg",
            "https://example.com/x.jpg",
            "https:///x.jpg",
            "not a [uri",
        ],
    )
    fun `asks only over https, on its own port, and only the hosts it was given`(refused: String) {
        assertThat(pictures.fetch(refused)).isNull()
        verify(http, never()).send(any(), any<HttpResponse.BodyHandler<InputStream>>())
    }

    @Test
    fun `asks an allowed host on the https port when it is written out`() {
        answer(200, byteArrayOf(1), "Content-Type" to "image/png")

        assertThat(pictures.fetch("https://images.igdb.com:443/x.png")).isNotNull
    }
}
