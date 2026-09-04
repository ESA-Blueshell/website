package net.blueshell.api.esports.domain

import net.blueshell.api.shared.seed.SeedCsv
import java.nio.charset.StandardCharsets.US_ASCII
import java.nio.file.Files
import java.nio.file.Path

/**
 * The art a seed names and the art committed beside it, as the two sets a rule compares.
 *
 * Bound to a seed rather than to the one the site ships, because the rules are asked twice: of
 * the fixture seed, where the cases that prove the rule live, and of the real one, where the
 * only question is whether today's files obey it.
 *
 * @param seed the files that name the art.
 * @param resourceRoot where those files live in the source tree, as `src/<set>/resources`.
 */
class ShippedArtInventory(private val seed: SeedCsv, private val resourceRoot: String) {

    /** Every picture a row names, once. */
    val named: Set<String> =
        (seed.rows("teams.csv").mapNotNull { it["banner"]?.ifBlank { null } } +
            seed.rows("banners.csv").map { it.getValue("banner") } +
            seed.rows("icons.csv").map { it.getValue("icon") })
            .toSet()

    /** Named art that nobody committed. */
    fun missing(): List<String> = named.filterNot { exists(it) }.sorted()

    /** Committed art that no row names. */
    fun orphaned(): List<String> = shipped().map { it.name }.filterNot { it in named }.sorted()

    /** Committed art larger than the box, as `name: WxH`. */
    fun oversized(maxWidth: Int, maxHeight: Int): List<String> =
        shipped().mapNotNull { file ->
            val (width, height) = sizeOf(file.path) ?: return@mapNotNull null
            if (width > maxWidth || height > maxHeight) "${file.name}: ${width}x$height" else null
        }.sorted()

    /** Committed art whose header could not be read, which a size rule would otherwise skip. */
    fun unreadable(): List<String> = shipped().filter { sizeOf(it.path) == null }.map { it.name }.sorted()

    /** The directory the art is read from, for a failure to name. */
    val directory: String get() = "$resourceRoot/${seed.directory}/art"

    private data class Art(val name: String, val path: Path)

    private fun exists(art: String): Boolean =
        javaClass.classLoader.getResource("${seed.directory}/art/$art.webp") != null

    /**
     * The committed pictures, read off the source tree rather than the classpath.
     *
     * What is being asked is which files a reviewer would see in the diff, and the classpath
     * answers a different question: it hands back a packaged copy whose url is not a directory
     * anybody can list. The source tree is the thing under review.
     */
    private fun shipped(): List<Art> {
        val directory = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .map { it.resolve("$resourceRoot/${seed.directory}/art") }
            .firstOrNull { Files.isDirectory(it) }
            ?: error("The art directory $resourceRoot/${seed.directory}/art is not below ${Path.of("").toAbsolutePath()}")
        return Files.list(directory).use { entries ->
            entries.map { it.fileName.toString() to it }
                .filter { (name, _) -> name.endsWith(".webp") }
                .map { (name, path) -> Art(name.removeSuffix(".webp"), path) }
                .toList()
        }
    }

    /**
     * How large a WebP is, read out of its header.
     *
     * Read here rather than through `ImageIO`, which has no WebP reader in the jdk: it answers that it cannot
     * read any of these files, every measurement comes back unknown, and a test that skips what it cannot
     * read passes against a directory full of 4K. A WebP is a RIFF container, and the size is in the first
     * chunk however the pixels are encoded: the canvas size for an extended file, and the frame's own for the
     * two plain ones. Little endian throughout.
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

    companion object {
        const val MAX_WIDTH = 2560
        const val MAX_HEIGHT = 1440
    }
}
