package net.blueshell.api.auth.domain.twofactor

import net.blueshell.api.platform.config.SettableClock
import net.blueshell.api.security.Browser
import net.blueshell.api.testsupport.UnitValkey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class ChallengesTest {
    private val clock = SettableClock().apply { set(Instant.parse("2026-09-24T12:00:00Z")) }
    private val challenges = Challenges(UnitValkey.template, clock)
    private val firefox = Browser("Firefox", "Linux")

    @BeforeEach
    fun setUp() = UnitValkey.flush()

    @Test
    fun `a challenge is found until five minutes pass`() {
        val opened = challenges.open(7, firefox)

        assertThat(challenges.find(opened.id)).isEqualTo(opened)
        clock.advance(Duration.ofMinutes(5).minusSeconds(1))
        assertThat(challenges.find(opened.id)).isNotNull()
        clock.advance(Duration.ofSeconds(1))
        assertThat(challenges.find(opened.id)).isNull()
        assertThat(challenges.find(null)).isNull()
        assertThat(challenges.find("nope")).isNull()
    }

    @Test
    fun `five wrong codes use a challenge up`() {
        val opened = challenges.open(7, firefox)

        assertThat((1..4).map { challenges.fail(opened) }).containsExactly(4, 3, 2, 1)
        assertThat(challenges.find(opened.id)?.wrongCodes).isEqualTo(4)
        assertThat(challenges.fail(opened)).isZero()
        assertThat(challenges.find(opened.id)).isNull()
    }

    @Test
    fun `a closed challenge is gone`() {
        val opened = challenges.open(7, firefox)

        challenges.close(opened.id)

        assertThat(challenges.find(opened.id)).isNull()
    }

    @Test
    fun `ten wrong codes in fifteen minutes stop an account's codes, and the count starts again after`() {
        val reached = (1..10).map { challenges.countFailure(7) }

        assertThat(reached.last()).isTrue()
        assertThat(reached.dropLast(1)).containsOnly(false)
        assertThat(challenges.isThrottled(7)).isTrue()
        assertThat(challenges.isThrottled(8)).isFalse()

        clock.advance(Duration.ofMinutes(15))
        assertThat(challenges.isThrottled(7)).isFalse()
        assertThat(challenges.countFailure(7)).isFalse()
        assertThat(challenges.isThrottled(7)).isFalse()
    }
}
