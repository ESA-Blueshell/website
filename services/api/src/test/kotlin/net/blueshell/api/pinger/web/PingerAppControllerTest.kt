package net.blueshell.api.pinger.web

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.pinger.api.PingerAppDownloads
import net.blueshell.api.pinger.api.PingerPlatform
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.net.URI

class PingerAppControllerTest {
    private val downloads = mockk<PingerAppDownloads>()
    private val controller = PingerAppController(downloads)

    @Test
    fun `a known os redirects to its installer`() {
        every { downloads.installerUrl(PingerPlatform.MACOS) } returns "https://host/app.dmg"

        val response = controller.downloadApp("macos")

        assertThat(response.statusCode).isEqualTo(HttpStatus.FOUND)
        assertThat(response.headers.location).isEqualTo(URI.create("https://host/app.dmg"))
    }

    @Test
    fun `an unknown os is not found`() {
        assertThatThrownBy { controller.downloadApp("solaris") }
            .isInstanceOf(ResponseStatusException::class.java)
            .extracting { (it as ResponseStatusException).statusCode }
            .isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `a known os with no asset on the release is not found`() {
        every { downloads.installerUrl(PingerPlatform.LINUX) } returns null

        assertThatThrownBy { controller.downloadApp("linux") }
            .isInstanceOf(ResponseStatusException::class.java)
            .extracting { (it as ResponseStatusException).statusCode }
            .isEqualTo(HttpStatus.NOT_FOUND)
    }
}
