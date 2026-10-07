package net.blueshell.api.pinger.web

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.pinger.api.PaintView
import net.blueshell.api.pinger.api.PingerPaintService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PingerControllerTest {
    private val service = mockk<PingerPaintService>()
    private val controller = PingerController(service)

    @Test
    fun `paint maps the current view`() {
        every { service.current() } returns PaintView("2001:db8::/64", 128, 1, 2, 3, 4, "/files/public/p.webp", true)

        val response = controller.paint()

        assertThat(response.prefix).isEqualTo("2001:db8::/64")
        assertThat(response.ratePps).isEqualTo(128)
        assertThat(response.originX).isEqualTo(1)
        assertThat(response.imageUrl).isEqualTo("/files/public/p.webp")
        assertThat(response.siteCieEnabled).isTrue()
    }

    @Test
    fun `setPaint passes the request through and maps the result`() {
        every { service.update("2001:db8::/64", 256, 10, 20, 30, 40, "p.webp", false) } returns
            PaintView("2001:db8::/64", 256, 10, 20, 30, 40, "/files/public/p.webp", false)

        val response = controller.setPaint(PaintRequest("2001:db8::/64", 256, 10, 20, 30, 40, "p.webp", false))

        verify { service.update("2001:db8::/64", 256, 10, 20, 30, 40, "p.webp", false) }
        assertThat(response.ratePps).isEqualTo(256)
        assertThat(response.width).isEqualTo(30)
        assertThat(response.siteCieEnabled).isFalse()
    }

    @Test
    fun `a request without an image leaves the prefix and image unset and SiteCie on`() {
        every { service.update(null, 128, 0, 0, 100, 100, null, true) } returns
            PaintView(null, 128, 0, 0, 100, 100, null, true)

        // Built with only the required box and rate, so the optional prefix, imagePath and SiteCie
        // toggle default; the toggle defaults to on.
        val response = controller.setPaint(PaintRequest(ratePps = 128, originX = 0, originY = 0, width = 100, height = 100))

        assertThat(response.prefix).isNull()
        assertThat(response.imageUrl).isNull()
        assertThat(response.siteCieEnabled).isTrue()
    }
}
