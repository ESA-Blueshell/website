package net.blueshell.api.shared.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Pinned to the same values as `address.test.ts`, the frontend twin's test. */
class PageAddressTest {
    @Test
    fun `keeps letters and digits of any alphabet, and makes everything else one hyphen`() {
        assertThat(addressOf("  Pokémon Fan Club!  ")).isEqualTo("pokémon-fan-club")
        assertThat(addressOf("Counter-Strike 2")).isEqualTo("counter-strike-2")
        assertThat(addressOf("--LAN / Cie--")).isEqualTo("lan-cie")
    }

    @Test
    fun `is never longer than an address column`() {
        assertThat(addressOf("a".repeat(100))).hasSize(ADDRESS_LENGTH)
    }
}
