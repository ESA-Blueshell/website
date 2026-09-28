package net.blueshell.api.game.domain

import net.blueshell.api.shared.seed.SeedCsv

/** The game seed files: the banner and icon each game ships with, and the art itself. */
object GameSeed {
    val files = SeedCsv("db/seed/games")
}
