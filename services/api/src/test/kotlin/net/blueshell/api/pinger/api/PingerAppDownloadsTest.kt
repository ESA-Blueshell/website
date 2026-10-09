package net.blueshell.api.pinger.api

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PingerAppDownloadsTest {
    private val releases = mockk<ReleaseAssets>()
    private val downloads = PingerAppDownloads(releases)

    @Test
    fun `it resolves the asset whose name matches the platform`() {
        every { releases.latest() } returns
            listOf(
                ReleaseAsset("blueshell-pinger-windows-amd64.exe", "https://host/helper.exe"),
                ReleaseAsset("Blueshell-Pinger.dmg", "https://host/app.dmg"),
            )

        assertThat(downloads.installerUrl(PingerPlatform.MACOS)).isEqualTo("https://host/app.dmg")
    }

    @Test
    fun `it matches the asset name case-insensitively`() {
        every { releases.latest() } returns listOf(ReleaseAsset("blueshell-pinger.exe", "https://host/app.exe"))

        assertThat(downloads.installerUrl(PingerPlatform.WINDOWS)).isEqualTo("https://host/app.exe")
    }

    @Test
    fun `it answers null when the release has no asset for the platform`() {
        every { releases.latest() } returns listOf(ReleaseAsset("Blueshell-Pinger.dmg", "https://host/app.dmg"))

        assertThat(downloads.installerUrl(PingerPlatform.LINUX)).isNull()
    }
}
