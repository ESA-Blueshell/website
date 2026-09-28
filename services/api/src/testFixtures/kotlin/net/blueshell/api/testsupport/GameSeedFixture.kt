package net.blueshell.api.testsupport

import net.blueshell.api.shared.seed.SeedCsv

/**
 * The game art a test loads instead of the art the site ships: a banner and an icon for each of
 * [EsportsSeedFixture]'s three games, whose records that seed adds.
 */
object GameSeedFixture {
    val files = SeedCsv("db/seed/games-fixtures")
}
