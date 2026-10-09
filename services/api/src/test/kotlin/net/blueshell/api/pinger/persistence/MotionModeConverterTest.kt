package net.blueshell.api.pinger.persistence

import net.blueshell.api.pinger.domain.MotionMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MotionModeConverterTest {
    private val converter = MotionModeConverter()

    @Test
    fun `a mode is stored as its lowercase name and read back`() {
        assertThat(converter.convertToDatabaseColumn(MotionMode.BOUNCE)).isEqualTo("bounce")
        assertThat(converter.convertToEntityAttribute("static")).isEqualTo(MotionMode.STATIC)
    }
}
