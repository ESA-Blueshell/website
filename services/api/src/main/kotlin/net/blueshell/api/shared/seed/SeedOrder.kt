package net.blueshell.api.shared.seed

/**
 * Records before art: both are ApplicationReadyEvent listeners now, so nothing else orders them.
 */
object SeedOrder {
    const val RECORDS = 100
    const val ART = 200
}
