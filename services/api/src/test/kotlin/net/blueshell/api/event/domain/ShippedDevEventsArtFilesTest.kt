package net.blueshell.api.event.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * The banners the event seed names and the banners the repository ships are the same set.
 *
 * Both failures are invisible where they happen: a row naming an uncommitted picture is a line
 * in a start-up log and an event drawn on the fallback template, and a committed picture no row
 * names is a banner published for no reason.
 */
class ShippedDevEventsArtFilesTest {
    private val named: List<String> =
        EventSeed.files.rows(EventSeed.EVENTS).mapNotNull { it["art"]?.ifBlank { null } }

    @Test
    fun `every banner the seed names is on the classpath`() {
        val missing = named.filterNot { exists(it) }.sorted()

        assertThat(missing)
            .describedAs("art a seed row names but nobody committed under %s/art", EventSeed.files.directory)
            .isEmpty()
    }

    @Test
    fun `every banner the repository ships is named by a row`() {
        val orphans = shipped().filterNot { it in named }.sorted()

        assertThat(orphans)
            .describedAs("art committed under %s/art that no row names", EventSeed.files.directory)
            .isEmpty()
    }

    @Test
    fun `no banner is named by two rows`() {
        // Each stored picture backs one event, storage being content-addressed.
        val twice = named.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()

        assertThat(twice).describedAs("art named by more than one event").isEmpty()
    }

    @Test
    fun `the seed names the art that is actually there`() {
        // A guard on the three above, which all pass against a seed that names nothing.
        assertThat(named).describedAs("events with a banner").hasSize(16)
    }

    private fun exists(art: String): Boolean =
        javaClass.classLoader.getResource("${EventSeed.files.directory}/art/$art") != null

    /** The committed pictures, read off the source tree: that is what a reviewer sees. */
    private fun shipped(): List<String> {
        val directory =
            generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .map { it.resolve("src/main/resources/${EventSeed.files.directory}/art") }
                .firstOrNull { Files.isDirectory(it) }
                ?: error("The shipped event art directory is not below ${Path.of("").toAbsolutePath()}")
        return Files.list(directory).use { entries ->
            entries.map { it.fileName.toString() }.filter { it.endsWith(".webp") }.toList()
        }
    }
}
