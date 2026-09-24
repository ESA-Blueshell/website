package net.blueshell.api.platform.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Clock
import java.time.Duration
import java.time.Instant

class ClockConfigTest {
    private val runner = ApplicationContextRunner().withUserConfiguration(ClockConfig::class.java)

    @Test
    fun `outside the test profile the clock is the system's and nothing can move it`() {
        runner.run { context ->
            assertThat(context.getBeansOfType(SettableClock::class.java)).isEmpty()
            assertThat(context.getBean(Clock::class.java)).isEqualTo(Clock.systemUTC())
        }
    }

    @Test
    fun `under the test profile the clock a rule reads is the one a test moves`() {
        runner.withPropertyValues("spring.profiles.active=test").run { context ->
            val clock = context.getBean(Clock::class.java)
            assertThat(clock).isInstanceOf(SettableClock::class.java)

            (clock as SettableClock).set(Instant.parse("2026-09-24T12:00:00Z"))
            clock.advance(Duration.ofMinutes(5))
            assertThat(clock.instant()).isEqualTo(Instant.parse("2026-09-24T12:05:00Z"))
            clock.reset()
            assertThat(clock.instant()).isNotEqualTo(Instant.parse("2026-09-24T12:05:00Z"))
        }
    }
}
