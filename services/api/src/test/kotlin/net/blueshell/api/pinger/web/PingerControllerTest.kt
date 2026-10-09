package net.blueshell.api.pinger.web

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.pinger.api.PaintView
import net.blueshell.api.pinger.api.PingerPaintService
import net.blueshell.api.pinger.api.PlacementMotion
import net.blueshell.api.pinger.api.PlacementView
import net.blueshell.api.pinger.domain.MotionMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant

class PingerControllerTest {
    private val service = mockk<PingerPaintService>(relaxUnitFun = true)
    private val stream = mockk<PaintStream>(relaxUnitFun = true)
    private val controller = PingerController(service, stream)
    private val now = Instant.parse("2026-10-09T20:00:00Z")

    private fun view(vararg placements: PlacementView) = PaintView("2001:db8::/64", 128, true, placements.toList(), now)

    private fun placement(id: Long) =
        PlacementView(id, "/files/public/p.webp", 10, 20, 30, 40, PlacementMotion(MotionMode.BOUNCE, 120.0, -80.0), now)

    @Test
    fun `paint maps the current view and its placements`() {
        every { service.current() } returns view(placement(1), placement(2))

        val response = controller.paint()

        assertThat(response.prefix).isEqualTo("2001:db8::/64")
        assertThat(response.placements.map { it.id }).containsExactly(1, 2)
        assertThat(response.placements[0].imageUrl).isEqualTo("/files/public/p.webp")
        assertThat(response.serverTime).isEqualTo(now)
        assertThat(response.placements[0].motion).isEqualTo(MotionResponse(MotionMode.BOUNCE, 120.0, -80.0))
        assertThat(response.placements[0].motionEpoch).isEqualTo(now)
    }

    @Test
    fun `paintStream opens a stream`() {
        val emitter = SseEmitter()
        every { stream.open() } returns emitter

        assertThat(controller.paintStream()).isSameAs(emitter)
    }

    @Test
    fun `setSettings passes the request through and maps the result`() {
        every { service.updateSettings("2001:db8::/64", 256, false) } returns PaintView("2001:db8::/64", 256, false, emptyList(), now)

        val response = controller.setSettings(PaintSettingsRequest("2001:db8::/64", 256, false))

        verify { service.updateSettings("2001:db8::/64", 256, false) }
        assertThat(response.ratePps).isEqualTo(256)
        assertThat(response.siteCieEnabled).isFalse()
        verify { stream.refresh() }
    }

    @Test
    fun `addPlacement passes the box through and maps the new placement`() {
        every { service.addPlacement("p.webp", 10, 20, 30, 40) } returns placement(7)

        val response = controller.addPlacement(PlacementRequest("p.webp", 10, 20, 30, 40))

        verify { service.addPlacement("p.webp", 10, 20, 30, 40) }
        assertThat(response.id).isEqualTo(7)
        verify { stream.refresh() }
    }

    @Test
    fun `movePlacement passes the id and box through`() {
        every { service.movePlacement(7, 1, 2, 3, 4) } returns placement(7)

        controller.movePlacement(7, PlacementBoxRequest(1, 2, 3, 4))

        verify { service.movePlacement(7, 1, 2, 3, 4) }
        verify { stream.refresh() }
    }

    @Test
    fun `setPlacementMotion passes the motion through and pushes the change`() {
        every { service.setMotion(7, MotionMode.BOUNCE, 120.0, -80.0) } returns placement(7)

        val response = controller.setPlacementMotion(7, PlacementMotionRequest(MotionMode.BOUNCE, 120.0, -80.0))

        assertThat(response.motion.mode).isEqualTo(MotionMode.BOUNCE)
        verify { stream.refresh() }
    }

    @Test
    fun `removePlacement delegates to the service`() {
        controller.removePlacement(7)

        verify { service.removePlacement(7) }
        verify { stream.refresh() }
    }
}
