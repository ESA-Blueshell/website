package net.blueshell.systemtests

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * `users` is unique on username, email, discord and phone_number, each per `deleted_at`. A
 * fixture value taken from a clock reading alone is therefore a collision waiting for two
 * builds in the same millisecond — which the api refuses correctly, and which reads as a flake
 * because the ordering that puts them together is not guaranteed (#1194).
 *
 * A counter alone is the opposite failure: it never repeats within a run and always repeats
 * across them, so the second run against a database the first one left behind is refused at
 * registration (#1222). Both properties are asserted here.
 *
 * Needs no browser and no stack.
 */
@Tag("system")
class UniqueFixtureValueTest {
    @Test
    fun `two suffixes taken in the same millisecond differ`() {
        val taken = (1..500).map { TestHelper.uniqueSuffix() }

        assertThat(taken).doesNotHaveDuplicates()
    }

    @Test
    fun `two phone numbers taken in the same millisecond differ`() {
        val taken = (1..500).map { TestHelper.uniquePhoneNumber() }

        assertThat(taken).doesNotHaveDuplicates()
    }

    /**
     * The property a counter cannot hold: two runs draw from the same eight digits, and what the
     * second one takes has to miss everything the first one left in the database. The two runs
     * are given their starts explicitly, so an implementation that ignores its start — a plain
     * counter — hands back two identical sequences and fails here.
     */
    @Test
    fun `a run cannot claim what another run took`() {
        val earlierRun = FixturePhoneNumbers(runStart = 0)
        val laterRun = FixturePhoneNumbers(runStart = 500)

        val taken = (1..500).map { earlierRun.next() }
        val takenLater = (1..500).map { laterRun.next() }

        assertThat(takenLater).doesNotContainAnyElementsOf(taken)
    }

    /**
     * Two runs that pick their own start must not pick the same one. A start read off the clock
     * passes this and still collides: it wraps the eight digits every 2.8 hours, which is an
     * afternoon's work apart, not a coincidence.
     */
    @Test
    fun `two runs do not start in the same place`() {
        val first = FixturePhoneNumbers()
        val second = FixturePhoneNumbers()

        assertThat(second.next()).isNotEqualTo(first.next())
    }

    /**
     * libphonenumber accepts `06` followed by 1-5 or 8, and a number outside that reads as a
     * form rejecting a valid member rather than as a bad fixture.
     */
    @Test
    fun `a phone number is one the details form accepts`() {
        val taken = (1..500).map { TestHelper.uniquePhoneNumber() }

        assertThat(taken).allSatisfy { number ->
            assertThat(number).matches("06[12345]\\d{7}")
        }
    }

    /**
     * A run that starts at the end of the eight digits wraps back to the front, and the digit
     * libphonenumber reads must survive the wrap like any other.
     */
    @Test
    fun `a phone number the run wraps around to is one the details form accepts`() {
        val lastRun = FixturePhoneNumbers(runStart = FixturePhoneNumbers.NUMBER_SPACE - 3)

        val taken = (1..10).map { lastRun.next() }

        assertThat(taken).doesNotHaveDuplicates()
        assertThat(taken).allSatisfy { number ->
            assertThat(number).matches("06[12345]\\d{7}")
        }
    }
}
