package net.blueshell.api.pinger.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PingerPlatformTest {
    @Test
    fun `an os value names its platform, case-insensitively`() {
        assertThat(PingerPlatform.of("macos")).isEqualTo(PingerPlatform.MACOS)
        assertThat(PingerPlatform.of("Linux")).isEqualTo(PingerPlatform.LINUX)
        assertThat(PingerPlatform.of("WINDOWS")).isEqualTo(PingerPlatform.WINDOWS)
    }

    @Test
    fun `an unknown os names no platform`() {
        assertThat(PingerPlatform.of("solaris")).isNull()
        assertThat(PingerPlatform.of("")).isNull()
    }

    @Test
    fun `each platform carries a distinct installer asset name`() {
        assertThat(PingerPlatform.MACOS.assetName).isEqualTo("Blueshell-Pinger.dmg")
        assertThat(PingerPlatform.LINUX.assetName).isEqualTo("Blueshell-Pinger-x86_64")
        assertThat(PingerPlatform.WINDOWS.assetName).isEqualTo("Blueshell-Pinger.exe")
    }
}
