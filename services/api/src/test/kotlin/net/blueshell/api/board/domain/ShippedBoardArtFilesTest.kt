package net.blueshell.api.board.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * The art the board seed files name and the art the repository ships are the same set, and no
 * two rows name the same file.
 *
 * Each failure is invisible where it happens: a row naming an uncommitted picture fails at
 * start-up as a line in a log, leaving the board drawn without a photograph; a committed picture
 * no row names is a photograph published for no reason; and a name used twice cannot work at
 * all, storage being content-addressed and `picture_id` unique on both tables. All three are
 * build defects, so all three fail the build.
 */
class ShippedBoardArtFilesTest {

    private val photos: List<String> =
        BoardSeed.files.rows("boards.csv").mapNotNull { it["photo"]?.ifBlank { null } }

    private val portraits: List<String> =
        BoardSeed.files.rows("members.csv").mapNotNull { it["portrait"]?.ifBlank { null } }

    private val named: List<String> = photos + portraits

    @Test
    fun `every picture the seed files name is on the classpath`() {
        val missing = named.filterNot { exists(it) }.sorted()

        assertThat(missing)
            .describedAs("art a seed file names but nobody committed under %s/art", BoardSeed.files.directory)
            .isEmpty()
    }

    @Test
    fun `every picture the repository ships is named by a seed file`() {
        val orphans = shipped().filterNot { it in named }.sorted()

        assertThat(orphans)
            .describedAs(
                "art committed under %s/art that no row names; bind it or take it back out",
                BoardSeed.files.directory,
            )
            .isEmpty()
    }

    /**
     * No two rows name one file, across both files at once.
     *
     * Across both rather than within each, because the tables are separate but the storage is
     * not: what a name resolves to is a path, and a path is a row in `files`. A board photo and
     * a portrait of the same bytes would land in different directories and so be different rows
     * — but there is no reason for one file to be named twice at all, and the two constraints
     * are easier to keep than to reason about.
     */
    @Test
    fun `no picture is named by two rows`() {
        val twice = named.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()

        assertThat(twice)
            .describedAs("art named by more than one row; a stored picture backs one record")
            .isEmpty()
    }

    @Test
    fun `the files name the art that is actually there`() {
        // A guard on the three above, which all pass against files that name nothing.
        assertThat(photos).describedAs("boards with a photograph").hasSize(5)
        assertThat(portraits).describedAs("members with a portrait").hasSize(21)
    }

    private fun exists(art: String): Boolean =
        javaClass.classLoader.getResource("${BoardSeed.files.directory}/art/$art.webp") != null

    /**
     * The committed pictures, read off the source tree rather than the classpath.
     *
     * What is being asked is which files a reviewer would see in the diff, and the classpath
     * answers a different question: it hands back a packaged copy whose url is not a directory
     * anybody can list. The source tree is the thing under review.
     */
    private fun shipped(): List<String> {
        val directory = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .map { it.resolve("src/main/resources/${BoardSeed.files.directory}/art") }
            .firstOrNull { Files.isDirectory(it) }
            ?: error("The shipped board art directory is not below ${Path.of("").toAbsolutePath()}")
        return Files.list(directory).use { entries ->
            entries.map { it.fileName.toString() }
                .filter { it.endsWith(".webp") }
                .map { it.removeSuffix(".webp") }
                .toList()
        }
    }
}
