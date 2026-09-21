package net.blueshell.api.file.domain

/**
 * An animation taken apart: every frame as it is displayed, and how long it is shown for.
 *
 * A frame is a file rather than a bitmap because the converter reads filenames and because a
 * long banner at 2560px would be a gigabyte of heap if the whole sequence were held decoded.
 * Each one is the whole canvas — offsets, transparency and disposal are already applied — so a
 * frame can be resized and re-encoded without any of the ones around it.
 */
class FrameSequence internal constructor(
    val frames: List<Frame>,
    val size: ImageDimensions.Size,
) : AutoCloseable {
    /** One frame, as PNG, and how long it is shown. */
    data class Frame(
        val bytes: ScratchFile,
        val durationMillis: Int,
    )

    override fun close() {
        frames.forEach { frame -> runCatching { frame.bytes.close() } }
    }
}
