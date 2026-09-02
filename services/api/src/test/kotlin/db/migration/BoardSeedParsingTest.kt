package db.migration

import net.blueshell.api.board.domain.BoardSeed
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * What the board seed files themselves say, read the way the migration reads them.
 *
 * The reader's own quoting rules are covered where the reader is. This is about the files: the
 * bytes on the classpath are what the association's history is now recorded in, so a name that
 * loses a character between the file and the parsed row has nowhere else to be caught.
 */
class BoardSeedParsingTest {

    private val boards = BoardSeed.files.rows("boards.csv")
    private val seats = BoardSeed.files.rows("seats.csv")

    @Test
    fun `the files hold the ten boards and the fifty-two seats on them`() {
        assertThat(boards.map { it.getValue("number") })
            .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
        assertThat(seats).hasSize(52)
    }

    @Test
    fun `the tenth board is seated before it takes office`() {
        // A candidate board is written down with its seats and without anything else: the six
        // of them have a name, a nickname and a role, and no blurb and no portrait yet.
        val tenth = seats.filter { it.getValue("board") == "10" }

        assertThat(tenth).hasSize(6)
        assertThat(tenth).allSatisfy {
            assertThat(it.getValue("name")).isNotBlank()
            assertThat(it.getValue("role")).isNotBlank()
            assertThat(it.getValue("description")).isEmpty()
            assertThat(it.getValue("portrait")).isEmpty()
        }
    }

    @Test
    fun `the boards run one after another with no gap and no overlap`() {
        // One board a year, changing in the autumn, so the line is a line: the ninth hands
        // over on a date that is actually recorded and the tenth takes office the day after.
        val terms = boards.map { it.getValue("start_date") to it.getValue("end_date") }

        terms.zipWithNext { earlier, later ->
            assertThat(LocalDate.parse(later.first))
                .isEqualTo(LocalDate.parse(earlier.second).plusDays(1))
        }
    }

    @Test
    fun `the files carry no byte-order mark`() {
        // A mark would land inside the first header name, so `number` would not be a column at
        // all and every row would fail to resolve.
        assertThat(BoardSeed.files.read("boards.csv")).startsWith("number,")
        assertThat(BoardSeed.files.read("seats.csv")).startsWith("board,")
    }

    @Test
    fun `a name written outside ASCII survives the read exactly`() {
        // İ is U+0130, the Turkish capital I with a dot, and ş is U+015F. Neither is the
        // ASCII letter it resembles, and the seed files are the only record of either.
        val seat = seats.single { it.getValue("board") == "5" && it.getValue("nickname") == "Vriendelijke kebab" }

        // Written as escapes as well as literally, so a mangled copy of this file cannot
        // agree with a mangled copy of the seed.
        assertThat(seat.getValue("name")).isEqualTo("İlayda Hotamiş")
        assertThat(seat.getValue("name")).isEqualTo("\u0130layda Hotami\u015F")
        assertThat(seat.getValue("name").map { it.code }).endsWith(0x015F)
    }

    @Test
    fun `an apostrophe in a board's name is one field and one character`() {
        // An apostrophe needs no quoting in a comma-separated field, so over-quoting it would
        // show up here as a quote inside the name.
        assertThat(boards.single { it.getValue("number") == "6" }.getValue("name"))
            .isEqualTo("Don't starve together")
    }

    @Test
    fun `a cheer carrying a comma is one field`() {
        assertThat(boards.single { it.getValue("number") == "5" }.getValue("cheer"))
            .isEqualTo("Blueshell, always ahead")
        assertThat(boards.single { it.getValue("number") == "8" }.getValue("cheer"))
            .isEqualTo("RNG, Be With Me!")
    }

    @Test
    fun `a nickname is recorded beside the name rather than in quotes inside it`() {
        // `Roos "SkyeWolf" Kruk` was one string in the migration these files replace.
        val seat = seats.single { it.getValue("name") == "Roos Kruk" }

        assertThat(seat.getValue("nickname")).isEqualTo("SkyeWolf")
        assertThat(seats.none { it.getValue("name").contains('"') }).isTrue()
    }

    @Test
    fun `every seat names a board the boards file lists`() {
        val numbers = boards.map { it.getValue("number") }.toSet()

        assertThat(seats.map { it.getValue("board") }.distinct()).allMatch { it in numbers }
    }

    @Test
    fun `a board's seats are recorded under names that tell them apart`() {
        // The seat's board and its recorded name are what identify one person's place on one
        // board, so two seats on a board cannot share a name.
        val duplicates = seats
            .groupBy { it.getValue("board") to it.getValue("name") }
            .filterValues { it.size > 1 }
            .keys

        assertThat(duplicates).isEmpty()
    }
}
