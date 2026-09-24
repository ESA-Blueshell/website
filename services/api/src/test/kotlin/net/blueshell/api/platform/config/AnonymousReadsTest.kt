package net.blueshell.api.platform.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AnonymousReadsTest {
    /** The Discord band sits on the front page, which nobody has logged in to see yet. */
    @Test
    fun `a visitor reads the Discord band and follows it live`() {
        assertThat(SecurityConfig.ANONYMOUS_READS).contains("/discord/live", "/discord/live/socket")
    }

    /** Every link into Discord on the site goes through these, including for visitors. */
    @Test
    fun `a visitor follows the site's links into Discord`() {
        assertThat(SecurityConfig.ANONYMOUS_READS).contains("/discord/invite/*", "/discord/channel/*")
    }

    @Test
    fun `nothing under management is read anonymously`() {
        assertThat(SecurityConfig.ANONYMOUS_READS.filter { it.startsWith("/management") }).isEmpty()
    }
}
