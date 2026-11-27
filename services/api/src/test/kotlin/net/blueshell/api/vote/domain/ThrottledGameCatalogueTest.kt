package net.blueshell.api.vote.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class ThrottledGameCatalogueTest {
    // A clock the waits move forward, so a test never sleeps.
    private class Ticking(
        var now: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now
    }

    private val clock = Ticking(Instant.parse("2026-12-10T18:00:00Z"))
    private val waits = mutableListOf<Duration>()
    private val igdb = mock<GameCatalogue>()
    private val celeste = CatalogueGame(26226, "Celeste", "co1", 2018)
    private val catalogue =
        ThrottledGameCatalogue(igdb, clock, perSecond = 2, remember = Duration.ofMinutes(10), patience = Duration.ofSeconds(2)) {
            waits += it
            clock.now = clock.now.plus(it)
        }

    init {
        whenever(igdb.search(any(), any(), anyOrNull())).thenReturn(listOf(celeste))
        whenever(igdb.byId(any())).thenReturn(celeste)
    }

    @Test
    fun `answers a search asked again from memory, whatever its case, until it is forgotten`() {
        assertThat(catalogue.search("Celeste", 10, null)).containsExactly(celeste)
        assertThat(catalogue.search("celeste", 10, null)).containsExactly(celeste)
        verify(igdb, times(1)).search(any(), any(), anyOrNull())

        catalogue.search("celeste", 10, 2018)
        verify(igdb, times(2)).search(any(), any(), anyOrNull())

        clock.now = clock.now.plus(Duration.ofMinutes(11))
        catalogue.search("celeste", 10, null)
        verify(igdb, times(3)).search(any(), any(), anyOrNull())
    }

    @Test
    fun `waits for a free slot once a second's lookups are spent, searches and lookups by id together`() {
        catalogue.search("a", 10, null)
        catalogue.byId(1)
        assertThat(waits).isEmpty()

        catalogue.byId(2)

        assertThat(waits).containsExactly(Duration.ofSeconds(1))
        verify(igdb, times(2)).byId(any())
    }

    @Test
    fun `refuses a lookup that would wait longer than it may`() {
        val impatient =
            ThrottledGameCatalogue(igdb, clock, perSecond = 1, patience = Duration.ofMillis(500)) { clock.now = clock.now.plus(it) }
        impatient.byId(1)

        assertThatThrownBy { impatient.byId(2) }.isInstanceOf(GameSearchUnavailable::class.java)
    }

    @Test
    fun `keeps to IGDB's own four a second unless told otherwise`() {
        val plain =
            ThrottledGameCatalogue(igdb, clock) {
                waits += it
                clock.now = clock.now.plus(it)
            }
        repeat(4) { plain.byId(it.toLong()) }
        assertThat(waits).isEmpty()

        plain.byId(5)

        assertThat(waits).containsExactly(Duration.ofSeconds(1))
    }

    @Test
    fun `a cover address is the catalogue's, and takes no turn`() {
        whenever(igdb.coverUrl("co1", CoverSize.BIG)).thenReturn("https://images.igdb.com/x.jpg")
        val strict = ThrottledGameCatalogue(igdb, clock, perSecond = 1) { error("no wait expected") }
        strict.byId(1)

        assertThat(strict.coverUrl("co1", CoverSize.BIG)).isEqualTo("https://images.igdb.com/x.jpg")
    }
}
