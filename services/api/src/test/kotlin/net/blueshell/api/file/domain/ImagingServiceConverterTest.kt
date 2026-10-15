package net.blueshell.api.file.domain

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import net.blueshell.api.shared.enums.FileType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path

/**
 * What the api sends the imaging service and what it makes of each answer. The service itself is
 * a stub here; `ImagingServiceIT` runs the real one.
 */
class ImagingServiceConverterTest {
    @TempDir
    private lateinit var directory: Path

    private val scratch: ScratchSpace by lazy { ScratchSpace(directory.resolve("scratch").toString()) }

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { it.start() }
    private var asked: String? = null
    private var sent: ByteArray? = null
    private var answer: (HttpExchange) -> Unit = { it.answer(200, "RIFFwebp", "Image-Width" to "2560", "Image-Height" to "1024") }

    init {
        server.createContext("/convert") { exchange ->
            asked = exchange.requestURI.rawQuery
            sent = exchange.requestBody.readAllBytes()
            answer(exchange)
        }
    }

    @AfterEach
    fun stop() = server.stop(0)

    private fun converter(url: String = "http://127.0.0.1:${server.address.port}/") = ImagingServiceConverter(url, scratch)

    private fun picture(bytes: String = "png bytes") = scratch.cut(".png").also { Files.writeString(it.path, bytes) }

    private fun leftInScratch() = Files.list(directory.resolve("scratch")).use { it.count() }

    @Test
    fun `a still master is sent with its kind's ceiling and quality, and kept with the size it was drawn at`() {
        val source = picture()

        val master = converter().master(source, "image/png", FileType.EVENT_BANNER)

        assertThat(asked).isEqualTo("max-edge=2560&quality=82")
        assertThat(sent).isEqualTo("png bytes".toByteArray())
        assertThat(master.size).isEqualTo(ImageDimensions.Size(2560, 1024))
        assertThat(Files.readString(master.bytes.path)).isEqualTo("RIFFwebp")
    }

    @Test
    fun `a lossless kind is sent as lossless`() {
        converter().master(picture(), "image/png", FileType.COMMITTEE_ICON)

        assertThat(asked).isEqualTo("max-edge=512&lossless=true")
    }

    @Test
    fun `an animation is sent as any picture is, and the service keeps its frames`() {
        converter().master(picture("GIF89a frames"), "image/gif", FileType.EVENT_BANNER)

        assertThat(asked).isEqualTo("max-edge=2560&quality=82")
        assertThat(sent).isEqualTo("GIF89a frames".toByteArray())
    }

    @Test
    fun `a picture the service will not decode is the uploader's to fix, and leaves nothing behind`() {
        answer = { it.answer(422, "the picture has too many pixels") }
        val source = picture()

        assertThatThrownBy { converter().master(source, "image/png", FileType.EVENT_BANNER) }
            .isInstanceOf(InvalidFileException::class.java)
            .hasMessageContaining("The picture has too many pixels")
        source.close()
        assertThat(leftInScratch()).isZero()
    }

    @Test
    fun `a picture larger than the service takes is refused the same way`() {
        answer = { it.answer(413, "the picture is too large") }

        assertThatThrownBy { converter().master(picture(), "image/png", FileType.EVENT_BANNER) }
            .isInstanceOf(InvalidFileException::class.java)
    }

    @Test
    fun `a service that cannot be reached is a 503, and leaves nothing behind`() {
        val closed = ServerSocket(0).use { it.localPort }
        val source = picture()

        assertThatThrownBy { converter("http://127.0.0.1:$closed").master(source, "image/png", FileType.EVENT_BANNER) }
            .isInstanceOf(ImagingUnavailableException::class.java)
        source.close()
        assertThat(leftInScratch()).isZero()
    }

    @Test
    fun `a service that fails on its own account is a 503`() {
        answer = { it.answer(503, "every conversion is busy") }

        assertThatThrownBy { converter().master(picture(), "image/png", FileType.EVENT_BANNER) }
            .isInstanceOf(ImagingUnavailableException::class.java)
            .hasMessageContaining("503")
    }

    @Test
    fun `an answer with no size is the service's fault`() {
        answer = { it.answer(200, "RIFFwebp") }

        assertThatThrownBy { converter().master(picture(), "image/png", FileType.EVENT_BANNER) }
            .isInstanceOf(ImagingUnavailableException::class.java)
    }

    @Test
    fun `an interrupted conversion keeps the interrupt and stores nothing`() {
        val source = picture()
        val converter = converter()
        Thread.currentThread().interrupt()
        try {
            assertThatThrownBy { converter.master(source, "image/png", FileType.EVENT_BANNER) }
                .isInstanceOf(ImagingUnavailableException::class.java)
        } finally {
            assertThat(Thread.interrupted()).isTrue()
        }
    }

    @Test
    fun `each width of a still is asked for at its size, measured against the recorded one`() {
        val recorded = ImageDimensions.Size(1600, 900)

        converter().renditionsOf(picture("webp master"), "image/webp", FileType.TEAM_BANNER, recorded, "a.webp").use { renditions ->
            assertThat(renditions.size).isEqualTo(recorded)
            scratch.cut(".webp").use { output ->
                renditions.write(ImageDimensions.Size(320, 180), output)
                assertThat(Files.readString(output.path)).isEqualTo("RIFFwebp")
            }
        }

        assertThat(asked).isEqualTo("width=320&height=180&quality=82")
        assertThat(sent).isEqualTo("webp master".toByteArray())
    }

    @Test
    fun `a width the service refuses is counted as refused rather than failing the ladder`() {
        answer = { it.answer(422, "the picture could not be decoded") }

        val recorded = ImageDimensions.Size(1600, 900)
        converter().renditionsOf(picture(), "image/webp", FileType.TEAM_BANNER, recorded, "a.webp").use { renditions ->
            scratch.cut(".webp").use { output ->
                assertThatThrownBy { renditions.write(ImageDimensions.Size(320, 180), output) }
                    .isInstanceOf(WebpConversionException::class.java)
            }
        }
    }

    private fun HttpExchange.answer(
        status: Int,
        body: String,
        vararg headers: Pair<String, String>,
    ) {
        headers.forEach { (name, value) -> responseHeaders.add(name, value) }
        val bytes = body.toByteArray()
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
