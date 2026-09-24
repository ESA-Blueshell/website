package net.blueshell.api.platform.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AnonymousReadsTest {
    /** The Discord band sits on the front page, which nobody has logged in to see yet. */
    @Test
    fun `a visitor reads the Discord band and follows it live`() {
        assertThat(SecurityConfig.ANONYMOUS_READS).contains("/discord/live", "/discord/live/socket", "/discord/live/mine")
    }

    /** Every link into Discord on the site goes through these, including for visitors. */
    @Test
    fun `a visitor follows the site's links into Discord`() {
        assertThat(SecurityConfig.ANONYMOUS_READS).contains("/discord/invite/*", "/discord/channel/*")
    }

    /** Account creation comes before any login, and the picker is on it. */
    @Test
    fun `a visitor searches the Discord server for themselves`() {
        assertThat(SecurityConfig.ANONYMOUS_READS).contains("/discord/members", "/discord/members/unclaimed")
        // The roles an event may ping are for whoever edits events, who is logged in.
        assertThat(SecurityConfig.ANONYMOUS_READS).doesNotContain("/discord/roles")
    }

    @Test
    fun `nothing under management is read anonymously`() {
        assertThat(SecurityConfig.ANONYMOUS_READS.filter { it.startsWith("/management") }).isEmpty()
    }
}
