package net.blueshell.api.contribution.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IngTextTest {
    @Test
    fun `strips accents from their letters rather than dropping the letters`() {
        assertThat(ingText("Zoë Bakker")).isEqualTo("Zoe Bakker")
        assertThat(ingText("Jürgen Maaß")).isEqualTo("Jurgen Maass")
        assertThat(ingText("Søren Ærø Łukasz")).isEqualTo("Soren AEro Lukasz")
    }

    @Test
    fun `keeps what ING takes and leaves out the rest`() {
        val taken = "Contributie 2026/27 (ESA) - Blueshell: ok? a.b,c 'x' +1"
        assertThat(ingText(taken)).isEqualTo(taken)
        assertThat(ingText("Tom & Jerry!  €5 ")).isEqualTo("Tom Jerry 5")
    }
}
