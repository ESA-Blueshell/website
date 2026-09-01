package net.blueshell.api.esports.domain

import net.blueshell.api.shared.seed.SeedCsv

/**
 * The esports seed files, and the one place their directory is named.
 *
 * The reader itself is shared — see [SeedCsv] for why there is only one — and this binds it to
 * the directory the esports history ships in. The migration that loads the rows, the start-up
 * step that puts the art on them and the tests that check the two agree all read from here.
 */
object EsportsSeed {

    /** The recovered esports history, as files on the classpath. */
    val files = SeedCsv("db/seed/esports")
}
