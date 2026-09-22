package net.blueshell.api.event.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * What the event seed files themselves say, read the way the loader reads them.
 *
 * The descriptions are poster captions with commas, quotes and line breaks in them, so a row
 * that shifts by a field is the failure to expect: a place would land in a start time, and
 * every page drawing the seed would be drawing something the site never said.
 */
class EventSeedParsingTest {
    private val committees = EventSeed.files.rows(EventSeed.COMMITTEES)
    private val events = EventSeed.files.rows(EventSeed.EVENTS)

    @Test
    fun `the files hold the committees and the events read off the site`() {
        assertThat(committees).hasSize(14)
        assertThat(events).hasSize(60)
    }

    @Test
    fun `every event names a committee the committees file has`() {
        val named = committees.map { it.getValue("name") }.toSet()

        assertThat(events.map { it.getValue("committee") }).isSubsetOf(named)
    }

    @Test
    fun `every event has a title and runs forwards`() {
        assertThat(events).allSatisfy { row ->
            assertThat(row.getValue("title")).isNotBlank()
            val start = Instant.parse(row.getValue("start_time"))
            assertThat(Instant.parse(row.getValue("end_time"))).isAfterOrEqualTo(start)
        }
    }

    @Test
    fun `the flags are written as a boolean and nothing else`() {
        assertThat(events.flatMap { listOf(it.getValue("members_only"), it.getValue("sign_up")) })
            .containsOnly("true", "false")
    }

    @Test
    fun `a description written over several lines survives the read whole`() {
        val captioned = events.first { it.getValue("description").contains("\n") }

        assertThat(captioned.getValue("title")).isNotBlank()
        assertThat(captioned.getValue("start_time")).startsWith("20")
    }

    @Test
    fun `the files carry no byte-order mark`() {
        // A mark would land inside the first header name, so every row would fail to resolve.
        assertThat(EventSeed.files.read(EventSeed.COMMITTEES)).startsWith("name,")
        assertThat(EventSeed.files.read(EventSeed.EVENTS)).startsWith("source_id,")
    }
}
