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
}
