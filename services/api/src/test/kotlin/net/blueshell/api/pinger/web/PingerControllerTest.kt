package net.blueshell.api.pinger.web

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.pinger.api.PaintView
import net.blueshell.api.pinger.api.PingerPaintService
import net.blueshell.api.pinger.api.PlacementView
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PingerControllerTest {
    private val service = mockk<PingerPaintService>(relaxUnitFun = true)
    private val controller = PingerController(service)

    private fun view(vararg placements: PlacementView) = PaintView("2001:db8::/64", 128, true, placements.toList())

    private fun placement(id: Long) = PlacementView(id, "/files/public/p.webp", 10, 20, 30, 40)

    @Test
    fun `paint maps the current view and its placements`() {
        every { service.current() } returns view(placement(1), placement(2))

        val response = controller.paint()

        assertThat(response.prefix).isEqualTo("2001:db8::/64")
        assertThat(response.placements.map { it.id }).containsExactly(1, 2)
        assertThat(response.placements[0].imageUrl).isEqualTo("/files/public/p.webp")
    }

    @Test
    fun `setSettings passes the request through and maps the result`() {
        every { service.updateSettings("2001:db8::/64", 256, false) } returns PaintView("2001:db8::/64", 256, false, emptyList())

        val response = controller.setSettings(PaintSettingsRequest("2001:db8::/64", 256, false))

        verify { service.updateSettings("2001:db8::/64", 256, false) }
        assertThat(response.ratePps).isEqualTo(256)
        assertThat(response.siteCieEnabled).isFalse()
    }

    @Test
    fun `addPlacement passes the box through and maps the new placement`() {
        every { service.addPlacement("p.webp", 10, 20, 30, 40) } returns placement(7)

        val response = controller.addPlacement(PlacementRequest("p.webp", 10, 20, 30, 40))

        verify { service.addPlacement("p.webp", 10, 20, 30, 40) }
        assertThat(response.id).isEqualTo(7)
    }

    @Test
    fun `movePlacement passes the id and box through`() {
        every { service.movePlacement(7, 1, 2, 3, 4) } returns placement(7)

        controller.movePlacement(7, PlacementBoxRequest(1, 2, 3, 4))

        verify { service.movePlacement(7, 1, 2, 3, 4) }
    }

    @Test
    fun `removePlacement delegates to the service`() {
        controller.removePlacement(7)

        verify { service.removePlacement(7) }
    }
}
