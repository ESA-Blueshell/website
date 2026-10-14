package net.blueshell.api.file.domain

import net.blueshell.api.shared.enums.FileType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.IOException

/**
 * The converter run inside the api: ImageIO for GIF frames and the libwebp tools for the rest.
 *
 * Where frames will not co-operate, a picture is drawn still rather than not at all: a banner
 * that does not move still keeps a phone from fetching the master.
 */
@Component
class InProcessImageConverter(
    private val scratch: ScratchSpace,
    private val webpEncoder: WebpEncoder,
    private val animated: AnimatedImages,
) : ImageConverter {
    override fun master(
        source: ScratchFile,
        declaredMediaType: String,
        type: FileType,
    ): ImageConverter.Converted {
        val maxEdge = requireNotNull(type.maxImageEdge) { "$type is stored as it was sent" }
        if (animated.mayAnimate(declaredMediaType)) {
            animatedMaster(source, type, declaredMediaType, maxEdge)?.let { return it }
        }

        // A GIF of one frame is still a GIF, which the still converter will not read.
        val readable = animated.readableStillOf(source, declaredMediaType)
        try {
            val input = readable ?: source
            val sourceSize =
                input.open().use(ImageDimensions::of)
                    ?: throw InvalidFileException("The uploaded image could not be decoded")
            val targetSize = sourceSize.fittedWithin(maxEdge)
            return encodedInto(targetSize) { webp ->
                webpEncoder.encode(input, webp, type.webpQuality, type.webpLossless, targetSize.takeIf { it != sourceSize })
            }
        } finally {
            readable?.close()
        }
    }

    /**
     * The master of a picture with more than one frame, or nothing where it has only one.
     *
     * Every frame is brought under the ceiling, not just the first: a banner that animates is
     * still a banner, and half a ladder of full-size frames is what the ceiling exists to stop.
     */
    private fun animatedMaster(
        source: ScratchFile,
        type: FileType,
        declaredMediaType: String,
        maxEdge: Int,
    ): ImageConverter.Converted? =
        animated.framesOf(source, declaredMediaType)?.use { frames ->
            val targetSize = frames.size.fittedWithin(maxEdge)
            encodedInto(targetSize) { webp ->
                animated.write(frames, webp, type.webpQuality, type.webpLossless, targetSize.takeIf { it != frames.size })
            }
        }

    private fun encodedInto(
        size: ImageDimensions.Size,
        encode: (ScratchFile) -> Unit,
    ): ImageConverter.Converted {
        val webp = scratch.cut(".webp")
        var encoded = false
        try {
            encode(webp)
            encoded = true
            return ImageConverter.Converted(webp, size)
        } finally {
            if (!encoded) webp.close()
        }
    }

    override fun renditionsOf(
        master: ScratchFile,
        mediaType: String,
        type: FileType,
        recorded: ImageDimensions.Size,
        name: String,
    ): ImageConverter.Renditions {
        val frames = framesOf(master, mediaType, name)
        // A GIF is not something the still converter will read, whatever its frames did.
        val still = frames?.frames?.first()?.bytes ?: animated.readableStillOf(master, mediaType)
        return Ladder(type, name, frames, still ?: master, ownsStill = frames == null && still != null, size = frames?.size ?: recorded)
    }

    /**
     * The frames of a picture that has more than one, or nothing.
     *
     * A picture whose frames cannot be read is not refused here. It falls back to the still
     * ladder, which is a banner drawn at every width without moving rather than a banner every
     * visitor downloads at full size.
     */
    private fun framesOf(
        master: ScratchFile,
        mediaType: String,
        name: String,
    ): FrameSequence? {
        if (!animated.mayAnimate(mediaType)) return null
        return try {
            animated.framesOf(master, mediaType)
        } catch (e: WebpConversionException) {
            log.warn("[image-renditions] the frames of {} could not be read, so it is stored still", name, e)
            null
        } catch (e: IOException) {
            log.warn("[image-renditions] the frames of {} could not be read: {}", name, e.message)
            null
        }
    }

    private inner class Ladder(
        private val type: FileType,
        private val name: String,
        private val frames: FrameSequence?,
        private val still: ScratchFile,
        private val ownsStill: Boolean,
        override val size: ImageDimensions.Size,
    ) : ImageConverter.Renditions {
        /** An animation that will not reassemble at one width is written as its first frame at that width. */
        override fun write(
            size: ImageDimensions.Size,
            output: ScratchFile,
        ) {
            if (frames == null) {
                stillOf(size, output)
                return
            }
            try {
                animated.write(frames, output, type.webpQuality, type.webpLossless, size)
            } catch (e: WebpConversionException) {
                log.warn("[image-renditions] {} would not reassemble at {}px, so that width is still", name, size.width, e)
                stillOf(size, output)
            }
        }

        private fun stillOf(
            size: ImageDimensions.Size,
            output: ScratchFile,
        ) {
            webpEncoder.encode(still, output, type.webpQuality, type.webpLossless, size)
        }

        override fun close() {
            frames?.close()
            if (ownsStill) still.close()
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(InProcessImageConverter::class.java)
    }
}
