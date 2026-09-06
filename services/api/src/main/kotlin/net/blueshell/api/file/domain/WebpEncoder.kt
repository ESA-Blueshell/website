package net.blueshell.api.file.domain

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Encodes prepared image files with the platform libwebp binary. */
@Component
class WebpEncoder(
    @Value($$"${app.files.cwebp-path}") private val binary: String,
) {
    @PostConstruct
    fun verifyAvailable() {
        val probe = run(listOf(binary, "-version"), "probe cwebp")
        if (probe.exitCode != 0) {
            throw WebpUnavailableException(
                "Could not probe cwebp with '$binary' (exit ${probe.exitCode}): ${probe.output}",
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
        val command = buildList {
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

    /**
     * The converter, run to completion.
     *
     * Being unable to run it at all is this deployment's problem and is raised here. What a
     * converter that did run made of its input is the caller's to read: a non-zero exit means
     * one thing when probing the install and another when converting somebody's picture.
     */
    private fun run(command: List<String>, action: String): Outcome {
        val process = try {
            ProcessBuilder(command).redirectErrorStream(true).start()
        } catch (e: IOException) {
            throw WebpUnavailableException("Could not $action with '${command.first()}': ${e.message}", e)
        }

        // The converter is drained on a thread of its own, so that the timeout below is the
        // one thing that decides how long this waits. Read inline it would not be: a child
        // that filled the pipe would block before it could exit, and reading a hung child's
        // output before the wait has been checked waits on it for as long as it hangs.
        val output = StringBuilder()
        val drain = Thread.ofVirtual().start {
            runCatching {
                process.inputStream.bufferedReader().use { reader -> reader.forEachLine(output::appendLine) }
            }
        }

        val finished = try {
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

    private data class Outcome(val exitCode: Int, val output: String)

    private companion object {
        val log = LoggerFactory.getLogger(WebpEncoder::class.java)
        const val DEFAULT_QUALITY = 82
        const val COMMAND_TIMEOUT_SECONDS = 30L
    }
}
