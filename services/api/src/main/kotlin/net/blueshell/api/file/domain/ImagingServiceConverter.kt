package net.blueshell.api.file.domain

import net.blueshell.api.shared.enums.FileType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration

/**
 * The converter in the imaging service, which decodes uploaded stills away from the api
 * (architecture ADR-014). Chosen wherever `app.imaging.url` names one.
 *
 * A picture with more than one frame is still taken apart here, by [InProcessImageConverter],
 * until the service converts animations too (#2447). A service that cannot be reached is an
 * [ImagingUnavailableException], which a master meets before anything is stored.
 */
@Component
@Primary
@ConditionalOnExpression($$"!'${app.imaging.url:}'.isBlank()")
class ImagingServiceConverter(
    @Value($$"${app.imaging.url}") url: String,
    private val scratch: ScratchSpace,
    private val animated: AnimatedImages,
    private val inProcess: InProcessImageConverter,
) : ImageConverter {
    private val endpoint = url.trimEnd('/') + "/convert"
    private val client =
        HttpClient
            .newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build()

    override fun master(
        source: ScratchFile,
        declaredMediaType: String,
        type: FileType,
    ): ImageConverter.Converted {
        if (moves(source, declaredMediaType)) return inProcess.master(source, declaredMediaType, type)

        val maxEdge = requireNotNull(type.maxImageEdge) { "$type is stored as it was sent" }
        val webp = scratch.cut(".webp")
        var converted = false
        try {
            val size = convert(source, webp, "max-edge=$maxEdge&${encodingOf(type)}") { reason -> InvalidFileException(reason) }
            converted = true
            return ImageConverter.Converted(webp, size)
        } finally {
            if (!converted) webp.close()
        }
    }

    override fun renditionsOf(
        master: ScratchFile,
        mediaType: String,
        type: FileType,
        recorded: ImageDimensions.Size,
        name: String,
    ): ImageConverter.Renditions {
        if (moves(master, mediaType)) return inProcess.renditionsOf(master, mediaType, type, recorded, name)

        return object : ImageConverter.Renditions {
            override val size = recorded

            override fun write(
                size: ImageDimensions.Size,
                output: ScratchFile,
            ) {
                convert(master, output, "width=${size.width}&height=${size.height}&${encodingOf(type)}") { reason ->
                    log.warn("[image-renditions] the imaging service refused {} at {}px: {}", name, size.width, reason)
                    WebpConversionException()
                }
            }

            override fun close() = Unit
        }
    }

    /** Whether the picture has frames to replay, asked of its header as the renditions are. */
    private fun moves(
        picture: ScratchFile,
        mediaType: String,
    ): Boolean = animated.animates(mediaType) { picture.open() }

    /** What cwebp was told when the api ran it: lossless, or the kind's quality. */
    private fun encodingOf(type: FileType): String =
        if (type.webpLossless) "lossless=true" else "quality=${type.webpQuality ?: DEFAULT_QUALITY}"

    /**
     * [input] converted into [output] at the target [query] names, answering the size it was
     * drawn at. A picture the service refuses becomes what [refused] makes of its reason.
     */
    private fun convert(
        input: ScratchFile,
        output: ScratchFile,
        query: String,
        refused: (String) -> RuntimeException,
    ): ImageDimensions.Size {
        val request =
            HttpRequest
                .newBuilder(URI.create("$endpoint?$query"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofFile(input.path))
                .build()
        val response = send(request)
        response.body().use { body ->
            return when (response.statusCode()) {
                HttpStatus.OK.value() -> {
                    Files.copy(body, output.path, StandardCopyOption.REPLACE_EXISTING)
                    sizeOf(response)
                }
                HttpStatus.CONTENT_TOO_LARGE.value(), HttpStatus.UNPROCESSABLE_CONTENT.value() ->
                    throw refused(reasonIn(body))
                else -> throw ImagingUnavailableException("The imaging service answered ${response.statusCode()}: ${reasonIn(body)}")
            }
        }
    }

    private fun send(request: HttpRequest): HttpResponse<InputStream> =
        try {
            client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        } catch (e: IOException) {
            throw ImagingUnavailableException("The imaging service could not be reached: ${e.message}", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ImagingUnavailableException("Interrupted while converting an image", e)
        }

    private fun sizeOf(response: HttpResponse<*>): ImageDimensions.Size {
        val width = headerOf(response, "Image-Width")
        val height = headerOf(response, "Image-Height")
        if (width == null || height == null) throw ImagingUnavailableException("The imaging service answered no size")
        return ImageDimensions.Size(width, height)
    }

    private fun headerOf(
        response: HttpResponse<*>,
        name: String,
    ): Int? =
        response
            .headers()
            .firstValue(name)
            .map(String::toIntOrNull)
            .orElse(null)

    /** The service's own words, which name no path: a sentence such as "the picture has too many pixels". */
    private fun reasonIn(body: InputStream): String =
        body
            .readNBytes(MAX_REASON_BYTES)
            .decodeToString()
            .trim()
            .replaceFirstChar(Char::titlecase)

    private companion object {
        val log = LoggerFactory.getLogger(ImagingServiceConverter::class.java)
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)

        // The service waits up to 30 seconds for a free conversion and then converts.
        val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(90)
        const val DEFAULT_QUALITY = 82
        const val MAX_REASON_BYTES = 200
    }
}
