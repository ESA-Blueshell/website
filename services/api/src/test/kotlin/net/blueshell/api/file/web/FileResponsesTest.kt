package net.blueshell.api.file.web

import net.blueshell.api.file.api.InMemoryBlobStore
import net.blueshell.api.file.domain.FileNotFoundException
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * What a request for stored bytes gets back, and which store answered.
 *
 * The two stores are the point: uploads and the art the release ships are separate namespaces,
 * so a key in one is not a key in the other. That used to need a servlet context and a volume
 * to check, because the answer was built inside the service every other module calls.
 */
class FileResponsesTest {

    private val uploads = InMemoryBlobStore(mapOf("$BANNERS/$HASH.webp" to BYTES))
    private val assets = InMemoryBlobStore(mapOf("logo.png" to BYTES))
    private val responses = FileResponses(uploads, assets)

    @Nested
    inner class TheSplitBetweenAssetsAndUploads {

        @Test
        fun `an upload is served from the uploads store`() {
            val answer = responses.publicFile(stored("$BANNERS/$HASH.webp", "image/webp"))

            assertThat(answer.body?.inputStream?.readBytes()).isEqualTo(BYTES)
        }

        @Test
        fun `an asset is served from the assets store`() {
            val answer = responses.asset("logo.png")

            assertThat(answer.body?.inputStream?.readBytes()).isEqualTo(BYTES)
            assertThat(answer.headers.contentType.toString()).isEqualTo("image/png")
        }

        @Test
        fun `an upload cannot be reached through the asset route`() {
            assertThatThrownBy { responses.asset("$BANNERS/$HASH.webp") }
                .isInstanceOf(FileNotFoundException::class.java)
        }

        @Test
        fun `an asset cannot be reached through the upload route`() {
            assertThatThrownBy { responses.publicFile(stored("logo.png", "image/png")) }
                .isInstanceOf(FileNotFoundException::class.java)
        }
    }

    @Nested
    inner class WhatTheBrowserIsTold {

        @Test
        fun `a public file is drawn rather than downloaded, under the extension it really has`() {
            val answer = responses.publicFile(stored("$BANNERS/$HASH.webp", "image/webp", name = "holiday.jpg"))

            assertThat(answer.headers.contentDisposition.isInline).isTrue()
            assertThat(answer.headers.contentDisposition.filename).isEqualTo("holiday.webp")
            assertThat(answer.headers.contentType.toString()).isEqualTo("image/webp")
        }

        @Test
        fun `a public file may be held forever, because its url is a hash of its bytes`() {
            val answer = responses.publicFile(stored("$BANNERS/$HASH.webp", "image/webp"))

            assertThat(answer.headers.cacheControl).contains("immutable").contains("max-age=31536000")
        }

        @Test
        fun `an attachment is downloaded under the name it was uploaded as`() {
            val answer = responses.attachment(stored("$BANNERS/$HASH.webp", "image/webp", name = "holiday.jpg"))

            assertThat(answer.headers.contentDisposition.isAttachment).isTrue()
            assertThat(answer.headers.contentDisposition.filename).isEqualTo("holiday.jpg")
            assertThat(answer.headers.cacheControl).doesNotContain("immutable")
        }

        @Test
        fun `the length is known before the bytes are read, so a download shows a progress bar`() {
            val answer = responses.publicFile(stored("$BANNERS/$HASH.webp", "image/webp"))

            assertThat(answer.body?.contentLength()).isEqualTo(BYTES.size.toLong())
        }
    }

    private fun stored(path: String, mediaType: String, name: String = "holiday.webp") = File(
        name = name,
        path = path,
        uploader = mock<User>(),
        mediaType = mediaType,
        type = FileType.TEAM_BANNER,
    )

    private companion object {
        const val BANNERS = "team-banners"
        const val HASH = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
        val BYTES = "not really a picture".toByteArray()
    }
}
