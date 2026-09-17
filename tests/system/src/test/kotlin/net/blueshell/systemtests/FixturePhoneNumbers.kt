package net.blueshell.systemtests

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong

/**
 * Dutch mobile numbers the details form accepts, no two of which a database ever sees twice.
 *
 * Eight digits follow the `06`, and libphonenumber accepts only 1-5 or 8 in the first of them, so
 * a run draws from five times ten million numbers and nothing in a number says which run wrote
 * it. The step rules out a repeat inside the run; `runStart` rules out a repeat against what an
 * earlier run left in a database that is not thrown away, as a developer's is not (#1222).
 *
 * The clock cannot serve as `runStart`: it wraps this space every 2.8 hours, and two runs that
 * far apart is an afternoon's work rather than a coincidence.
 */
class FixturePhoneNumbers(private val runStart: Long) {

    constructor() : this(SecureRandom().nextLong(NUMBER_SPACE))

    private val taken = AtomicLong()

    fun next(): String {
        val slot = Math.floorMod(runStart + taken.incrementAndGet(), NUMBER_SPACE)
        val subscriberDigit = SUBSCRIBER_DIGITS[(slot / SUBSCRIBER_BLOCK).toInt()]
        val rest = "%07d".format(slot % SUBSCRIBER_BLOCK)
        return "06$subscriberDigit$rest"
    }

    companion object {
        /** The digits libphonenumber accepts right after the `06`, minus 8 to keep the fold even. */
        private const val SUBSCRIBER_DIGITS = "12345"
        private const val SUBSCRIBER_BLOCK = 10_000_000L

        /** How many numbers a run can take before it meets its own first one. */
        const val NUMBER_SPACE: Long = 5 * SUBSCRIBER_BLOCK
    }
}
