package net.blueshell.api.platform.statistics

import net.blueshell.api.board.api.BoardCounts
import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.esports.api.EsportsCounts
import net.blueshell.api.event.api.EventService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class AssociationStatisticsReaderTest {

    private val committees: CommitteeService = mock()
    private val boards: BoardCounts = mock()
    private val esports: EsportsCounts = mock()
    private val events: EventService = mock()

    private val reader = AssociationStatisticsReader(committees, boards, esports, events)

    /** A fixed moment, so the window the reader asks for can be asserted exactly. */
    private val now = LocalDateTime.parse("2026-09-04T12:00:00")

    @Test
    fun `every number comes from the module that owns the records behind it`() {
        whenever(esports.gamesCurrentlyPlayed()).thenReturn(6)
        whenever(esports.seasonsPlayed()).thenReturn(11)
        whenever(esports.teamsFieldedNow()).thenReturn(13)
        whenever(committees.count()).thenReturn(15)
        whenever(boards.count()).thenReturn(9)
        whenever(events.countBetween(any(), any())).thenReturn(104)

        val read = reader.read(now)

        assertThat(read).isEqualTo(
            AssociationStatistics(
                gamesPlayed = 6,
                seasonsPlayed = 11,
                committees = 15,
                boards = 9,
                teamsThisSeason = 13,
                eventsLastYear = 104,
            )
        )
    }

    @Test
    fun `the events counted are the last rolling year, not the academic one`() {
        whenever(events.countBetween(any(), any())).thenReturn(0)

        reader.read(now)

        val from = argumentCaptor<LocalDateTime>()
        val to = argumentCaptor<LocalDateTime>()
        verify(events).countBetween(from.capture(), to.capture())
        assertThat(from.firstValue).isEqualTo(LocalDateTime.parse("2025-09-04T12:00:00"))
        assertThat(to.firstValue).isEqualTo(now)
    }

    /** Nothing recorded yet reads as zero rather than as an absent number. */
    @Test
    fun `an association with nothing recorded answers zeroes`() {
        whenever(esports.gamesCurrentlyPlayed()).thenReturn(0)
        whenever(esports.seasonsPlayed()).thenReturn(0)
        whenever(esports.teamsFieldedNow()).thenReturn(0)
        whenever(committees.count()).thenReturn(0)
        whenever(boards.count()).thenReturn(0)
        whenever(events.countBetween(any(), any())).thenReturn(0)

        assertThat(reader.read(now)).isEqualTo(AssociationStatistics(0, 0, 0, 0, 0, 0))
    }
}
