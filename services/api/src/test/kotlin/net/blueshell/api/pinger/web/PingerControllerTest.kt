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
        every { service.current() } returns PaintView("2001:db8::/64", 128, 1, 2, 3, 4, "/files/public/p.webp")

        val response = controller.paint()

        assertThat(response.prefix).isEqualTo("2001:db8::/64")
        assertThat(response.ratePps).isEqualTo(128)
        assertThat(response.originX).isEqualTo(1)
        assertThat(response.imageUrl).isEqualTo("/files/public/p.webp")
    }

    @Test
    fun `setPaint passes the request through and maps the result`() {
        every { service.update("2001:db8::/64", 256, 10, 20, 30, 40, "p.webp") } returns
            PaintView("2001:db8::/64", 256, 10, 20, 30, 40, "/files/public/p.webp")

        val response = controller.setPaint(PaintRequest("2001:db8::/64", 256, 10, 20, 30, 40, "p.webp"))

        verify { service.update("2001:db8::/64", 256, 10, 20, 30, 40, "p.webp") }
        assertThat(response.ratePps).isEqualTo(256)
        assertThat(response.width).isEqualTo(30)
    }
}
