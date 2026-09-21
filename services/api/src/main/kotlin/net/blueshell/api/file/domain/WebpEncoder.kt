package net.blueshell.api.file.domain

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Encodes prepared image files with the platform libwebp binaries.
 *
 * Three of them, because one animation needs all three: `cwebp` writes a still, `dwebp` reads
 * one back, and `webpmux` is the only one of the set that will take stills apart and put them
 * together again. `gif2webp` is deliberately not among them — it writes an animation but will
 * not resize one, so a ladder built on it would still need every frame handled separately, and
 * a GIF's frames are read here rather than by it.
 */
@Component
class WebpEncoder(
    @Value($$"${app.files.cwebp-path}") private val binary: String,
    @Value($$"${app.files.dwebp-path:dwebp}") private val decoderBinary: String = "dwebp",
    @Value($$"${app.files.webpmux-path:webpmux}") private val muxerBinary: String = "webpmux",
) {
    @PostConstruct
    fun verifyAvailable() {
        // Every binary an upload could reach, so a half-installed image fails at deploy rather
        // than on the first animated banner somebody posts.
        probe(binary, "cwebp")
        probe(decoderBinary, "dwebp")
        probe(muxerBinary, "webpmux")
    }

    private fun probe(
        candidate: String,
        name: String,
    ) {
        val probe = run(listOf(candidate, "-version"), "probe $name")
        if (probe.exitCode != 0) {
            throw WebpUnavailableException(
                "Could not probe $name with '$candidate' (exit ${probe.exitCode}): ${probe.output}",
            )
        }
    }

    /**
     * Converts one working copy into another.
     *
     * Takes files rather than bytes because the converter is a subprocess that reads a filename
     * off a command line — the one place in this module where where the bytes sit is not a
     * detail. [ScratchSpace] is what supplies both.
     */
    fun encode(
        input: ScratchFile,
        output: ScratchFile,
        quality: Int?,
        lossless: Boolean,
        resize: ImageDimensions.Size? = null,
    ) = encode(input.path, output.path, quality, lossless, resize)

    private fun encode(
        input: Path,
        output: Path,
        quality: Int?,
        lossless: Boolean,
        resize: ImageDimensions.Size? = null,
    ) {
        val command =
            buildList {
                add(binary)
                add("-quiet")
                if (lossless) {
                    add("-lossless")
                } else {
                    add("-q")
                    add((quality ?: DEFAULT_QUALITY).toString())
                }
                if (resize != null) {
                    add("-resize")
                    add(resize.width.toString())
                    add(resize.height.toString())
                }
                add(input.toString())
                add("-o")
                add(output.toString())
            }
        val encoded = run(command, "encode WebP")
        if (encoded.exitCode != 0) {
            log.warn("The converter refused an upload (exit {}): {}", encoded.exitCode, encoded.output)
            throw WebpConversionException()
        }
    }

    /** One still frame of [animation], written to [output] as WebP. */
    fun frameOf(
        animation: ScratchFile,
        index: Int,
        output: ScratchFile,
    ) {
        val command =
            listOf(muxerBinary, "-get", "frame", index.toString(), animation.path.toString(), "-o", output.path.toString())
        val got = run(command, "read a frame")
        if (got.exitCode != 0) {
            log.warn("The converter refused a frame (exit {}): {}", got.exitCode, got.output)
            throw WebpConversionException()
        }
    }

    /** [input] decoded to PNG, so that a frame can be read as a bitmap. */
    fun decode(
        input: ScratchFile,
        output: ScratchFile,
    ) {
        val command = listOf(decoderBinary, "-quiet", input.path.toString(), "-o", output.path.toString())
        val decoded = run(command, "decode WebP")
        if (decoded.exitCode != 0) {
            log.warn("The converter refused a frame (exit {}): {}", decoded.exitCode, decoded.output)
            throw WebpConversionException()
        }
    }

    /** What [animation] says about itself, or nothing where it is a still. */
    fun animationOf(animation: ScratchFile): WebpAnimation? {
        val info = run(listOf(muxerBinary, "-info", animation.path.toString()), "read an animation")
        if (info.exitCode != 0) {
            log.warn("The converter refused an animation (exit {}): {}", info.exitCode, info.output)
            throw WebpConversionException()
        }
        return WebpAnimation.of(info.output)
    }

    /**
     * [frames] assembled into one animation at [output], each drawn over the whole canvas and
     * disposing of nothing.
     *
     * Deliberately not the smallest encoding: libwebp would store a frame as the rectangle that
     * changed and blend it onto the one before, which is fewer bytes and leaves every frame
     * meaningless on its own. These frames are independent, so reading this animation back is
     * pulling out a frame rather than replaying the ones before it — which is what lets a
     * rendition be derived from a stored master at all.
     */
    fun mux(
        frames: List<AnimationFrame>,
        output: ScratchFile,
    ) {
        require(frames.isNotEmpty()) { "An animation needs at least one frame" }
        val command =
            buildList {
                add(muxerBinary)
                frames.forEach { frame ->
                    add("-frame")
                    add(frame.bytes.path.toString())
                    add("+${frame.durationMillis}+0+0+0-b")
                }
                add("-loop")
                add("0")
                add("-o")
                add(output.path.toString())
            }
        val muxed = run(command, "assemble WebP")
        if (muxed.exitCode != 0) {
            log.warn("The converter refused an animation (exit {}): {}", muxed.exitCode, muxed.output)
            throw WebpConversionException()
        }
    }

    /**
     * The converter, run to completion.
     *
     * Being unable to run it at all is this deployment's problem and is raised here. What a
     * converter that did run made of its input is the caller's to read: a non-zero exit means
     * one thing when probing the install and another when converting somebody's picture.
     */
    private fun run(
        command: List<String>,
        action: String,
    ): Outcome {
        val process =
            try {
                ProcessBuilder(command).redirectErrorStream(true).start()
            } catch (e: IOException) {
                throw WebpUnavailableException("Could not $action with '${command.first()}': ${e.message}", e)
            }

        // The converter is drained on a thread of its own, so that the timeout below is the
        // one thing that decides how long this waits. Read inline it would not be: a child
        // that filled the pipe would block before it could exit, and reading a hung child's
        // output before the wait has been checked waits on it for as long as it hangs.
        val output = StringBuilder()
        val drain =
            Thread.ofVirtual().start {
                runCatching {
                    process.inputStream.bufferedReader().use { reader -> reader.forEachLine(output::appendLine) }
                }
            }

        val finished =
            try {
                process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                process.destroyForcibly()
                Thread.currentThread().interrupt()
                throw WebpUnavailableException("Interrupted while trying to $action", e)
            }
        if (!finished) {
            process.destroyForcibly()
            drain.join()
            throw WebpUnavailableException("Timed out trying to $action with '${command.first()}'")
        }

        drain.join()
        return Outcome(process.exitValue(), output.toString().trim())
    }

    private data class Outcome(
        val exitCode: Int,
        val output: String,
    )

    /** One frame on its way into an animation: its bytes, and how long it is shown. */
    data class AnimationFrame(
        val bytes: ScratchFile,
        val durationMillis: Int,
    )

    private companion object {
        val log = LoggerFactory.getLogger(WebpEncoder::class.java)
        const val DEFAULT_QUALITY = 82
        const val COMMAND_TIMEOUT_SECONDS = 30L
    }
}
