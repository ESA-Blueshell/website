package net.blueshell.api.file.domain

import org.w3c.dom.Element
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadata
import kotlin.math.max

/**
 * The frames of a GIF, as they are drawn rather than as they are stored.
 *
 * A GIF frame is a rectangle patched onto whatever the frames before it left on the canvas, and
 * the reader hands over that rectangle. Replaying the patches here is the difference between an
 * animation that survives the conversion and one whose frames are torn fragments on a
 * transparent background.
 */
object GifFrames {
    /**
     * [source] taken apart, or nothing where it holds one frame or cannot be read as a GIF.
     *
     * One frame is a still, whatever the container: it gets the ordinary ladder rather than an
     * animation of length one.
     */
    fun of(
        source: Path,
        scratch: ScratchSpace,
    ): FrameSequence? =
        ImageIO.createImageInputStream(source.toFile())?.use { stream ->
            val readers = ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) return@use null
            val reader = readers.next()
            try {
                reader.input = stream
                if (reader.getNumImages(true) < 2) return@use null
                read(reader, scratch)
            } finally {
                reader.dispose()
            }
        }

    /**
     * The first frame of [source] as PNG, or nothing where it is not a GIF.
     *
     * The still converter will not read a GIF at all, so a GIF that holds one frame — and a GIF
     * whose frames could not be replayed — needs its bytes handed over in something that
     * converter does read.
     */
    fun firstFrameOf(
        source: Path,
        scratch: ScratchSpace,
    ): ScratchFile? =
        ImageIO.createImageInputStream(source.toFile())?.use { stream ->
            val readers = ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) return@use null
            val reader = readers.next()
            try {
                reader.input = stream
                pngOf(reader.read(reader.minIndex), scratch)
            } finally {
                reader.dispose()
            }
        }

    // The frames already written have to be cleaned up whatever came out of the ones after
    // them, including an Error, and the failure is rethrown untouched.
    @Suppress("TooGenericExceptionCaught")
    private fun read(
        reader: javax.imageio.ImageReader,
        scratch: ScratchSpace,
    ): FrameSequence {
        val count = reader.getNumImages(true)
        val canvasSize = canvasOf(reader, count)
        val canvas = BufferedImage(canvasSize.width, canvasSize.height, BufferedImage.TYPE_INT_ARGB)
        val frames = mutableListOf<FrameSequence.Frame>()
        var previous: BufferedImage? = null
        try {
            for (index in 0 until count) {
                val control = controlOf(reader.getImageMetadata(index))
                val placement = placementOf(reader.getImageMetadata(index))
                val patch = reader.read(index)
                if (control.disposal == RESTORE_TO_PREVIOUS) previous = copyOf(canvas)

                val graphics = canvas.createGraphics()
                try {
                    graphics.drawImage(patch, placement.first, placement.second, null)
                } finally {
                    graphics.dispose()
                }

                frames += FrameSequence.Frame(pngOf(canvas, scratch), control.durationMillis)

                when (control.disposal) {
                    RESTORE_TO_BACKGROUND -> clear(canvas, placement, patch.width, patch.height)
                    RESTORE_TO_PREVIOUS -> previous?.let { restore(canvas, it) }
                    else -> Unit
                }
            }
        } catch (e: Throwable) {
            frames.forEach { frame -> runCatching { frame.bytes.close() } }
            throw e
        }
        return FrameSequence(frames, canvasSize)
    }

    /**
     * How large the animation is.
     *
     * The logical screen the file declares, where it declares one large enough. A GIF whose
     * frames reach outside it exists, and a canvas that cropped them would drop what the
     * committee drew, so the frames have the final say.
     */
    private fun canvasOf(
        reader: javax.imageio.ImageReader,
        count: Int,
    ): ImageDimensions.Size {
        val declared = logicalScreenOf(reader)
        var width = declared?.width ?: 0
        var height = declared?.height ?: 0
        for (index in 0 until count) {
            val placement = placementOf(reader.getImageMetadata(index))
            width = max(width, placement.first + reader.getWidth(index))
            height = max(height, placement.second + reader.getHeight(index))
        }
        return ImageDimensions.Size(max(1, width), max(1, height))
    }

    private fun logicalScreenOf(reader: javax.imageio.ImageReader): ImageDimensions.Size? =
        runCatching {
            val metadata = reader.streamMetadata ?: return null
            val descriptor = elementOf(metadata, "LogicalScreenDescriptor") ?: return null
            ImageDimensions.Size(
                descriptor.getAttribute("logicalScreenWidth").toInt(),
                descriptor.getAttribute("logicalScreenHeight").toInt(),
            )
        }.getOrNull()

    /** Where this frame's rectangle sits on the canvas. */
    private fun placementOf(metadata: IIOMetadata): Pair<Int, Int> {
        val descriptor = elementOf(metadata, "ImageDescriptor") ?: return 0 to 0
        val left = descriptor.getAttribute("imageLeftPosition").toIntOrNull() ?: 0
        val top = descriptor.getAttribute("imageTopPosition").toIntOrNull() ?: 0
        return left to top
    }

    /**
     * How long this frame is shown and what happens to it afterwards.
     *
     * A GIF counts in hundredths of a second, and a file that asks for none or for one is asking
     * faster than any browser will draw: both are held at the tenth of a second every renderer
     * substitutes, so a converted animation runs at the speed it was being watched at.
     */
    private fun controlOf(metadata: IIOMetadata): Control {
        val control = elementOf(metadata, "GraphicControlExtension")
        val hundredths = control?.getAttribute("delayTime")?.toIntOrNull() ?: 0
        val disposal = control?.getAttribute("disposalMethod").orEmpty()
        return Control(
            durationMillis = if (hundredths <= 1) DEFAULT_DELAY_MILLIS else hundredths * 10,
            disposal = disposal,
        )
    }

    private fun elementOf(
        metadata: IIOMetadata,
        name: String,
    ): Element? {
        val root = metadata.getAsTree(metadata.nativeMetadataFormatName) ?: return null
        val nodes = (root as? Element)?.getElementsByTagName(name) ?: return null
        return nodes.item(0) as? Element
    }

    private fun clear(
        canvas: BufferedImage,
        at: Pair<Int, Int>,
        width: Int,
        height: Int,
    ) {
        val graphics = canvas.createGraphics()
        try {
            graphics.composite = java.awt.AlphaComposite.Clear
            graphics.fillRect(at.first, at.second, width, height)
        } finally {
            graphics.dispose()
        }
    }

    private fun restore(
        canvas: BufferedImage,
        saved: BufferedImage,
    ) {
        val graphics = canvas.createGraphics()
        try {
            graphics.composite = java.awt.AlphaComposite.Src
            graphics.drawImage(saved, 0, 0, null)
        } finally {
            graphics.dispose()
        }
    }

    private fun copyOf(canvas: BufferedImage): BufferedImage =
        BufferedImage(canvas.width, canvas.height, BufferedImage.TYPE_INT_ARGB).also { copy ->
            val graphics = copy.createGraphics()
            try {
                graphics.drawImage(canvas, 0, 0, null)
            } finally {
                graphics.dispose()
            }
        }

    /** The canvas as it stands, on a disk for the converter to pick up. */
    // The cleanup has to run whatever came out of the write, including an Error.
    @Suppress("TooGenericExceptionCaught")
    private fun pngOf(
        canvas: BufferedImage,
        scratch: ScratchSpace,
    ): ScratchFile {
        val file = scratch.cut(".png")
        try {
            java.nio.file.Files.newOutputStream(file.path).use { out -> ImageIO.write(copyOf(canvas), "png", out) }
        } catch (e: Throwable) {
            file.close()
            throw e
        }
        return file
    }

    private data class Control(
        val durationMillis: Int,
        val disposal: String,
    )

    private const val RESTORE_TO_BACKGROUND = "restoreToBackgroundColor"
    private const val RESTORE_TO_PREVIOUS = "restoreToPrevious"
    private const val DEFAULT_DELAY_MILLIS = 100
}
