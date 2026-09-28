package net.blueshell.api.discord.domain

import net.blueshell.api.platform.config.SettableClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class KeptReadTest {
    @Test
    fun `serves the kept answer while fresh, reads again after, and serves the last answer while Discord fails`() {
        val clock = SettableClock().also { it.set(Instant.parse("2026-09-28T10:00:00Z")) }
        val kept = KeptRead<String>("Discord test", Duration.ofMinutes(5), clock)
        var reads = 0

        assertThat(kept.get { error("down") }).isNull()
        assertThat(kept.get { null }).isNull()
        assertThat(kept.get { "first".also { reads++ } }).isEqualTo("first")

        clock.advance(Duration.ofMinutes(5).minusSeconds(1))
        assertThat(kept.get { "second".also { reads++ } }).isEqualTo("first")
        assertThat(kept.get(again = true) { "asked again".also { reads++ } }).isEqualTo("asked again")

        clock.advance(Duration.ofMinutes(5))
        assertThat(kept.get { error("down") }).isEqualTo("asked again")
        assertThat(kept.get { null }).isEqualTo("asked again")
        assertThat(kept.get { "third".also { reads++ } }).isEqualTo("third")
        assertThat(reads).isEqualTo(3)
    }
}
