package net.blueshell.api.board.domain

import net.blueshell.api.shared.seed.SeedCsv

/**
 * The board seed files, and the one place their directory is named.
 *
 * The reader itself is shared — see [SeedCsv] for why there is only one — and this binds it to
 * the directory the association's boards ship in. The migration that loads the rows and the
 * tests that check what landed all read from here.
 */
object BoardSeed {

    /** The nine boards and their seats, as files on the classpath. */
    val files = SeedCsv("db/seed/boards")
}
