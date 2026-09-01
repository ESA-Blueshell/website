package db.migration

import net.blueshell.api.board.domain.BoardSeed
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
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
    fun `the files hold the ten boards and the forty-six seats on them`() {
        assertThat(boards.map { it.getValue("number") })
            .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
        assertThat(seats).hasSize(46)
    }

    @Test
    fun `the tenth board has no seats, and that is a board`() {
        // It is a candidate board: nobody has taken a seat on it yet.
        assertThat(seats.none { it.getValue("board") == "10" }).isTrue()
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

    /**
     * Every picture the files name, as the art directory would spell it.
     *
     * The rule is one substitution: the master for a row is that row's own `image` with
     * `.webp` for an extension. There is no lookup table to fall out of step.
     */
    private fun artNamed(rows: List<Map<String, String>>): List<String> =
        rows.map { it.getValue("image") }
            .filter { it.isNotBlank() }
            .map { "db/seed/boards/art/" + it.substringBeforeLast('.') + ".webp" }

    @Test
    fun `every picture the files name ships beside them`() {
        // The photographs are still asset names the frontend loads, and they are also the
        // masters the api will store. Naming one that is not here would be a board drawn
        // blank on a fresh database, which no test further down the stack would catch.
        val missing = (artNamed(boards) + artNamed(seats))
            .filter { javaClass.classLoader.getResource(it) == null }

        assertThat(missing).isEmpty()
    }

    @Test
    fun `the art directory holds nothing the files do not name`() {
        // The other direction: a picture nobody points at is megabytes nobody notices. Read
        // off the source tree rather than the classpath, because the packaged resources are
        // inside a jar by the time a test runs and a jar is not a directory to walk.
        val art = Path.of("src/main/resources/db/seed/boards/art")
        assertThat(Files.isDirectory(art))
            .describedAs("the shipped board art at %s", art.toAbsolutePath())
            .isTrue()

        val named = (artNamed(boards) + artNamed(seats)).toSet()
        val shipped = Files.walk(art).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".webp") }
                .map { "db/seed/boards/art/${it.parent.fileName}/${it.fileName}" }
                .toList()
        }

        assertThat(shipped).hasSize(26).allMatch { it in named }
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
