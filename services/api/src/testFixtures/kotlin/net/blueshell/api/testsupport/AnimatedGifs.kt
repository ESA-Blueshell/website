package net.blueshell.api.testsupport

import org.w3c.dom.Element
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.MemoryCacheImageOutputStream

/**
 * GIFs that animate, for the tests that convert one.
 *
 * Written here rather than checked in as bytes, so what each frame is supposed to contain is
 * readable beside the assertion that reads it back.
 */
object AnimatedGifs {
    /** One frame: what to draw, where to put it, how long to show it and what to do after. */
    data class Frame(
        val image: BufferedImage,
        val left: Int = 0,
        val top: Int = 0,
        val durationMillis: Int = 100,
        val disposal: String = "none",
    )

    /** A canvas-sized block of [color]. */
    fun block(
        width: Int,
        height: Int,
        color: Color,
    ): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { image ->
            val graphics = image.createGraphics()
            try {
                graphics.color = color
                graphics.fillRect(0, 0, width, height)
            } finally {
                graphics.dispose()
            }
        }

    /**
     * A GIF of three frames: a red canvas, a green patch left on it, and a blue patch that is
     * cleared again. Enough to tell a reader that replays the patches from one that does not.
     */
    fun patched(
        width: Int = 64,
        height: Int = 48,
    ): ByteArray =
        of(
            width,
            height,
            listOf(
                Frame(block(width, height, Color.RED), durationMillis = 120),
                Frame(block(PATCH, PATCH, Color.GREEN), left = PATCH, top = PATCH, durationMillis = 80),
                Frame(
                    block(PATCH, PATCH, Color.BLUE),
                    left = PATCH,
                    top = PATCH,
                    durationMillis = 200,
                    disposal = "restoreToBackgroundColor",
                ),
            ),
        )

    /** A GIF of one frame, which is a still however it is stored. */
    fun single(
        width: Int = 64,
        height: Int = 48,
    ): ByteArray = of(width, height, listOf(Frame(block(width, height, Color.RED))))

    fun of(
        width: Int,
        height: Int,
        frames: List<Frame>,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        MemoryCacheImageOutputStream(out).use { stream ->
            writer.output = stream
            writer.prepareWriteSequence(canvas(writer, width, height))
            frames.forEach { frame ->
                val type = javax.imageio.ImageTypeSpecifier.createFromRenderedImage(frame.image)
                val metadata = writer.getDefaultImageMetadata(type, writer.defaultWriteParam)
                writer.writeToSequence(IIOImage(frame.image, null, describe(metadata, frame)), null)
            }
            writer.endWriteSequence()
        }
        writer.dispose()
        return out.toByteArray()
    }

    /** The canvas every frame is placed on, which the patches are smaller than. */
    private fun canvas(
        writer: javax.imageio.ImageWriter,
        width: Int,
        height: Int,
    ): javax.imageio.metadata.IIOMetadata {
        val metadata = writer.getDefaultStreamMetadata(writer.defaultWriteParam)
        val format = metadata.nativeMetadataFormatName
        val root = metadata.getAsTree(format) as IIOMetadataNode
        nodeOf(root, "LogicalScreenDescriptor").apply {
            setAttribute("logicalScreenWidth", width.toString())
            setAttribute("logicalScreenHeight", height.toString())
            setAttribute("colorResolution", "8")
            setAttribute("pixelAspectRatio", "0")
        }
        metadata.setFromTree(format, root)
        return metadata
    }

    private fun describe(
        metadata: javax.imageio.metadata.IIOMetadata,
        frame: Frame,
    ): javax.imageio.metadata.IIOMetadata {
        val format = metadata.nativeMetadataFormatName
        val root = metadata.getAsTree(format) as IIOMetadataNode

        nodeOf(root, "GraphicControlExtension").apply {
            setAttribute("disposalMethod", frame.disposal)
            setAttribute("userInputFlag", "FALSE")
            setAttribute("transparentColorFlag", "FALSE")
            setAttribute("delayTime", (frame.durationMillis / 10).toString())
            setAttribute("transparentColorIndex", "0")
        }
        nodeOf(root, "ImageDescriptor").apply {
            setAttribute("imageLeftPosition", frame.left.toString())
            setAttribute("imageTopPosition", frame.top.toString())
            setAttribute("imageWidth", frame.image.width.toString())
            setAttribute("imageHeight", frame.image.height.toString())
            setAttribute("interlaceFlag", "FALSE")
        }
        metadata.setFromTree(format, root)
        return metadata
    }

    private fun nodeOf(
        root: IIOMetadataNode,
        name: String,
    ): IIOMetadataNode {
        val existing = root.getElementsByTagName(name)
        for (index in 0 until existing.length) {
            (existing.item(index) as? Element)?.let { return it as IIOMetadataNode }
        }
        return IIOMetadataNode(name).also(root::appendChild)
    }

    private const val PATCH = 10
}
