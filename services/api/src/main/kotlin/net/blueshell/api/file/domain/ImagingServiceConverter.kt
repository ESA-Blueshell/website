package net.blueshell.api.file.domain

import net.blueshell.api.shared.enums.FileType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
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

/** Converts through the imaging service; the api decodes nothing itself (architecture ADR-014). */
@Component
class ImagingServiceConverter(
    @Value($$"${app.imaging.url}") url: String,
    private val scratch: ScratchSpace,
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
    ): ImageConverter.Renditions =
        object : ImageConverter.Renditions {
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

    private fun encodingOf(type: FileType): String =
        if (type.webpLossless) "lossless=true" else "quality=${type.webpQuality ?: DEFAULT_QUALITY}"

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

    /** The service's reason names no path, so it is safe to show the uploader. */
    private fun reasonIn(body: InputStream): String =
        body
            .readNBytes(MAX_REASON_BYTES)
            .decodeToString()
            .trim()
            .replaceFirstChar(Char::titlecase)

    private companion object {
        val log = LoggerFactory.getLogger(ImagingServiceConverter::class.java)
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)

        // A 30s wait for a slot plus the longest conversion the frame limits allow.
        val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(90)
        const val DEFAULT_QUALITY = 82
        const val MAX_REASON_BYTES = 200
    }
}
