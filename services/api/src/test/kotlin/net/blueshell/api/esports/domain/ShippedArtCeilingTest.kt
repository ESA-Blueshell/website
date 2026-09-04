package net.blueshell.api.esports.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.charset.StandardCharsets.US_ASCII
import java.nio.file.Path

/**
 * The art the repository ships fits inside 1440p.
 *
 * A master wider than the widest width its kind is stored at is bytes that are only ever thrown away: every
 * copy a browser downloads is derived from it, and the ladder for a banner stops at 2560. So a 4K file in
 * here costs the repository, every clone and every image build, and buys a page nothing at all. Checked here
 * rather than written down somewhere, which is the kind of rule that holds until the next person adds a
 * picture: the failure is a page that weighs more than it should, which nobody notices by eye.
 */
class ShippedArtCeilingTest {

    @Test
    fun `no picture is wider or taller than 1440p`() {
        val over = art().mapNotNull { file ->
            val size = sizeOf(file) ?: return@mapNotNull null
            val (width, height) = size
            if (width > MAX_WIDTH || height > MAX_HEIGHT) "${file.fileName}: ${width}x$height" else null
        }.sorted()

        assertThat(over)
            .describedAs("shipped art over ${MAX_WIDTH}x$MAX_HEIGHT; fit it inside that box")
            .isEmpty()
    }

    /** A guard on the test above, which passes against a directory it failed to read. */
    @Test
    fun `there is art to measure, and every file of it could be read`() {
        val files = art()
        assertThat(files).isNotEmpty
        assertThat(files.filter { sizeOf(it) == null }.map { it.fileName.toString() })
            .describedAs("shipped art whose size could not be read")
            .isEmpty()
    }

    /**
     * How large a WebP is, read out of its header.
     *
     * Read here rather than through `ImageIO`, which has no WebP reader in the jdk: it answers that it cannot
     * read any of these files, every measurement comes back unknown, and a test that skips what it cannot
     * read passes against a directory full of 4K. Which is what the guard below caught when this was written
     * the obvious way. A WebP is a RIFF container, and the size is in the first chunk however the pixels are
     * encoded: the canvas size for an extended file, and the frame's own for the two plain ones. Little
     * endian throughout.
     */
    private fun sizeOf(file: Path): Pair<Int, Int>? {
        val bytes = Files.readAllBytes(file)
        if (bytes.size < 30) return null
        if (text(bytes, 0, 4) != "RIFF" || text(bytes, 8, 4) != "WEBP") return null
        return when (text(bytes, 12, 4)) {
            // 8 bytes of chunk header, a byte of flags and three reserved, then the canvas
            // width and height less one, three bytes each.
            "VP8X" -> (le(bytes, 24, 3) + 1) to (le(bytes, 27, 3) + 1)
            // 8 bytes of chunk header, a one-byte signature, then 14 bits of width less one
            // and 14 of height less one, packed.
            "VP8L" -> le(bytes, 21, 4).let { bits ->
                ((bits and 0x3FFF) + 1) to (((bits shr 14) and 0x3FFF) + 1)
            }
            // 8 bytes of chunk header, a three-byte frame tag, a three-byte sync code, then
            // 14 bits of width and 14 of height.
            "VP8 " -> (le(bytes, 26, 2) and 0x3FFF) to (le(bytes, 28, 2) and 0x3FFF)
            else -> null
        }
    }

    private fun text(bytes: ByteArray, at: Int, length: Int) = String(bytes, at, length, US_ASCII)

    private fun le(bytes: ByteArray, at: Int, length: Int): Int =
        (0 until length).sumOf { (bytes[at + it].toInt() and 0xFF) shl (8 * it) }

    /**
     * The committed pictures, read off the source tree for the same reason the sibling test
     * reads them there: the classpath hands back a packaged copy whose url is not a directory
     * anybody can list, and the source tree is the thing under review.
     */
    private fun art(): List<Path> {
        val directory = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .map { it.resolve("src/main/resources/${EsportsSeed.files.directory}/art") }
            .firstOrNull { Files.isDirectory(it) }
            ?: error("The shipped art directory is not below ${Path.of("").toAbsolutePath()}")
        return Files.list(directory).use { entries ->
            entries.filter { it.fileName.toString().endsWith(".webp") }.toList()
        }
    }

    private companion object {
        const val MAX_WIDTH = 2560
        const val MAX_HEIGHT = 1440
    }
}
