package net.blueshell.api.shared.seed

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * The reader's own directory, which is the part the seeds differ in.
 *
 * How a row parses is checked against the migration that reads these files — see
 * `EsportsSeedParsingTest` — because that is where a mis-parse lands as data. What is checked
 * here is that the directory is the reader's rather than the reader's own constant, so a second
 * seed can name a second one.
 */
class SeedCsvTest {

    @Test
    fun `a file is read from the directory the reader was given`() {
        assertThatThrownBy { SeedCsv("db/seed/nowhere").read("games.csv") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Seed file db/seed/nowhere/games.csv is missing")
    }
}
