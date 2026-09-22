package net.blueshell.api.shared.seed

/**
 * Records before art before sign-ups: all are ApplicationReadyEvent listeners, so nothing else
 * orders them.
 */
object SeedOrder {
    const val RECORDS = 100
    const val ART = 200
    const val SIGN_UPS = 300
}
