package net.blueshell.api.shared.seed

/**
 * Records before art before links before sign-ups: all are ApplicationReadyEvent listeners, so
 * nothing else orders them.
 */
object SeedOrder {
    const val RECORDS = 100
    const val ART = 200

    /** Between records two seeds write, once both stand. */
    const val LINKS = 250
    const val SIGN_UPS = 300
}
