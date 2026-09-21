package net.blueshell.api.file.domain

import org.springframework.stereotype.Component
import java.io.InputStream

/**
 * Animations, taken apart and put back together at whatever width is asked for.
 *
 * Neither converter will resize an animation, so every width is the same three steps: frames
 * out, each one resized as an ordinary still, frames back in. The two decoders exist because
 * the two stored formats do — a GIF is read by ImageIO, which has no reader for WebP at all,
 * and an animated WebP master is read back through the converter that wrote it.
 */
@Component
class AnimatedImages(
    private val scratch: ScratchSpace,
    private val webpEncoder: WebpEncoder,
) {
    /** Whether a stored picture of this media type could hold more than one frame. */
    fun mayAnimate(mediaType: String): Boolean = mediaType.substringBefore(';').trim() in ANIMATED_MEDIA_TYPES

    /**
     * Whether the stored bytes of a picture hold more than one frame, without taking it apart.
     *
     * Asked of every public picture at start, so a WebP answers from its container flags. A GIF
     * has no such flag, and there are a handful of them, so one is taken at its word and falls
     * back to a still when it turns out to hold one frame.
     */
    fun animates(
        mediaType: String,
        content: () -> InputStream,
    ): Boolean =
        when (mediaType.substringBefore(';').trim()) {
            GIF_MEDIA_TYPE -> true
            WEBP_MEDIA_TYPE -> content().use(WebpDimensions::isAnimated)
            else -> false
        }

    /**
     * [source] taken apart, or nothing where it holds a single frame.
     *
     * The caller closes what comes back. Nothing here decides what to do with a still: a
     * picture with one frame is a still, and the ordinary converter handles it.
     */
    fun framesOf(
        source: ScratchFile,
        mediaType: String,
    ): FrameSequence? =
        when (mediaType.substringBefore(';').trim()) {
            GIF_MEDIA_TYPE -> GifFrames.of(source.path, scratch)
            WEBP_MEDIA_TYPE -> webpFramesOf(source)
            else -> null
        }

    /**
     * [source] in something the still converter reads, or nothing where it reads [source]
     * already. The caller closes what comes back.
     *
     * `cwebp` refuses a GIF outright, so a GIF of one frame — and a GIF whose frames could not
     * be replayed — would have no ladder at all unless its bytes were handed over as something
     * else first.
     */
    fun readableStillOf(
        source: ScratchFile,
        mediaType: String,
    ): ScratchFile? =
        if (mediaType.substringBefore(';').trim() == GIF_MEDIA_TYPE) {
            GifFrames.firstFrameOf(source.path, scratch)
        } else {
            null
        }

    /**
     * [frames] written to [output] as one animation, each frame resized to [size] where one is
     * given and left alone where it is not.
     *
     * The stills are cut and closed here rather than handed out: they exist for the length of
     * one assembly and a caller that had to close them would be holding the converter's
     * scratch work.
     */
    fun write(
        frames: FrameSequence,
        output: ScratchFile,
        quality: Int?,
        lossless: Boolean,
        size: ImageDimensions.Size? = null,
    ) {
        val stills = mutableListOf<WebpEncoder.AnimationFrame>()
        try {
            frames.frames.forEach { frame ->
                val still = scratch.cut(".webp")
                stills += WebpEncoder.AnimationFrame(still, frame.durationMillis)
                webpEncoder.encode(frame.bytes, still, quality, lossless, size)
            }
            webpEncoder.mux(stills, output)
        } finally {
            stills.forEach { still -> runCatching { still.bytes.close() } }
        }
    }

    /**
     * An animated WebP taken apart, frame by frame, through the converter.
     *
     * Every frame of an animation this wrote covers the whole canvas and blends with nothing,
     * so pulling one out is enough — there is no earlier frame to replay onto it. An animation
     * from anywhere else may not be built that way, which is why this module writes its own.
     */
    // The frames already cut have to be cleaned up whatever came out of the ones after them,
    // including an Error, and the failure is rethrown untouched.
    @Suppress("TooGenericExceptionCaught")
    private fun webpFramesOf(source: ScratchFile): FrameSequence? {
        val animation = webpEncoder.animationOf(source) ?: return null
        val frames = mutableListOf<FrameSequence.Frame>()
        try {
            animation.durationsMillis.forEachIndexed { index, duration ->
                val encoded = scratch.cut(".webp")
                try {
                    // webpmux numbers frames from one.
                    webpEncoder.frameOf(source, index + 1, encoded)
                    val decoded = scratch.cut(".png")
                    frames += FrameSequence.Frame(decoded, duration)
                    webpEncoder.decode(encoded, decoded)
                } finally {
                    encoded.close()
                }
            }
        } catch (e: Throwable) {
            frames.forEach { frame -> runCatching { frame.bytes.close() } }
            throw e
        }
        val size = frames.first().bytes.open().use(ImageDimensions::of)
        if (size == null) {
            frames.forEach { frame -> runCatching { frame.bytes.close() } }
            return null
        }
        return FrameSequence(frames, size)
    }

    private companion object {
        const val GIF_MEDIA_TYPE = "image/gif"
        const val WEBP_MEDIA_TYPE = "image/webp"
        val ANIMATED_MEDIA_TYPES = setOf(GIF_MEDIA_TYPE, WEBP_MEDIA_TYPE)
    }
}
