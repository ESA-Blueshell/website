package net.blueshell.api.esports.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * The art the seed files name and the art the repository ships are the same set.
 *
 * Neither half of that is checked anywhere else. A row naming a picture nobody committed fails at start-up,
 * on a running deployment, as a line in a log — and the page it was meant for is simply drawn on yesterday's
 * picture, which is not a visible failure. A committed picture no row names is the opposite problem: this is
 * publisher art in a public repository, so a file that nothing uses is one nobody had a reason to publish.
 * Both are build defects, so both fail the build.
 */
class ShippedArtFilesTest {

    private val named: Set<String> =
        (EsportsSeed.files.rows("teams.csv").mapNotNull { it["banner"]?.ifBlank { null } } +
            EsportsSeed.files.rows("banners.csv").map { it.getValue("banner") } +
            EsportsSeed.files.rows("icons.csv").map { it.getValue("icon") })
            .toSet()

    @Test
    fun `every picture the seed files name is on the classpath`() {
        val missing = named.filterNot { exists(it) }.sorted()

        assertThat(missing)
            .describedAs("art a seed file names but nobody committed under %s/art", EsportsSeed.files.directory)
            .isEmpty()
    }

    @Test
    fun `every picture the repository ships is named by a seed file`() {
        val orphans = shipped().filterNot { it in named }.sorted()

        assertThat(orphans)
            .describedAs(
                "art committed under %s/art that no row names; bind it or hold it in gameart/",
                EsportsSeed.files.directory,
            )
            .isEmpty()
    }

    @Test
    fun `the files name art at all`() {
        // A guard on the two above, which both pass against a pair of files that name nothing.
        assertThat(named).isNotEmpty()
    }

    private fun exists(art: String): Boolean =
        javaClass.classLoader.getResource("${EsportsSeed.files.directory}/art/$art.webp") != null

    /**
     * The committed pictures, read off the source tree rather than the classpath.
     *
     * What is being asked is which files a reviewer would see in the diff, and the classpath
     * answers a different question: it hands back a packaged copy whose url is not a directory
     * anybody can list. The source tree is the thing under review.
     */
    private fun shipped(): List<String> {
        val directory = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .map { it.resolve("src/main/resources/${EsportsSeed.files.directory}/art") }
            .firstOrNull { Files.isDirectory(it) }
            ?: error("The shipped art directory is not below ${Path.of("").toAbsolutePath()}")
        return Files.list(directory).use { entries ->
            entries.map { it.fileName.toString() }
                .filter { it.endsWith(".webp") }
                .map { it.removeSuffix(".webp") }
                .toList()
        }
    }
}
