package net.blueshell.api.pinger.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.file.api.StoredPictures
import net.blueshell.api.pinger.domain.MotionMode
import net.blueshell.api.pinger.persistence.PingerPaint
import net.blueshell.api.pinger.persistence.PingerPaintRepository
import net.blueshell.api.pinger.persistence.PingerPlacement
import net.blueshell.api.pinger.persistence.PingerPlacementRepository
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class PingerPaintServiceTest {
    private val repository = mockk<PingerPaintRepository>()
    private val placements = mockk<PingerPlacementRepository>(relaxUnitFun = true)
    private val pictures = mockk<StoredPictures>()
    private val now = Instant.parse("2026-10-09T20:00:10Z")
    private val service = PingerPaintService(repository, placements, pictures, Clock.fixed(now, ZoneOffset.UTC))

    private fun row() = PingerPaint(ratePps = 128)

    private fun placement(
        id: Long,
        path: String = "pinger-paint/a.webp",
        ordinal: Int = 0,
    ) = PingerPlacement(
        imagePath = path,
        originX = 100,
        originY = 200,
        width = 800,
        height = 600,
        ordinal = ordinal,
        motionEpoch = Instant.parse("2026-10-09T20:00:00Z"),
    ).apply { this.id = id }

    @Test
    fun `current reads the settings and the placements in order, resolving each image url`() {
        every { repository.findById(1L) } returns Optional.of(row().apply { prefix = "2001:db8::/64" })
        every { placements.findAllByOrderByOrdinalAscIdAsc() } returns
            listOf(placement(1, "pinger-paint/a.webp", 0), placement(2, "pinger-paint/b.webp", 1))

        val view = service.current()

        assertThat(view.prefix).isEqualTo("2001:db8::/64")
        assertThat(view.placements.map { it.id }).containsExactly(1, 2)
        assertThat(view.placements[0].imageUrl).isEqualTo("/files/public/pinger-paint/a.webp")
        assertThat(view.placements[1].imageUrl).isEqualTo("/files/public/pinger-paint/b.webp")
        assertThat(view.placements[0].motion).isEqualTo(PlacementMotion(MotionMode.STATIC, 0.0, 0.0))
        assertThat(view.placements[0].motionEpoch).isEqualTo(Instant.parse("2026-10-09T20:00:00Z"))
        assertThat(view.serverTime).isEqualTo(now)
    }

    @Test
    fun `current falls back to the defaults when the settings row is missing`() {
        every { repository.findById(1L) } returns Optional.empty()
        every { placements.findAllByOrderByOrdinalAscIdAsc() } returns emptyList()

        val view = service.current()

        assertThat(view.ratePps).isEqualTo(128)
        assertThat(view.siteCieEnabled).isTrue()
        assertThat(view.placements).isEmpty()
    }

    @Test
    fun `updateSettings saves the prefix, rate and SiteCie toggle`() {
        every { repository.findById(1L) } returns Optional.of(row())
        every { repository.save(any()) } answers { firstArg() }
        every { placements.findAllByOrderByOrdinalAscIdAsc() } returns emptyList()

        val view = service.updateSettings("2001:db8::/64", 256, siteCieEnabled = false)

        assertThat(view.ratePps).isEqualTo(256)
        assertThat(view.prefix).isEqualTo("2001:db8::/64")
        assertThat(view.siteCieEnabled).isFalse()
        verify { repository.save(any()) }
    }

    @Test
    fun `updateSettings refuses a rate outside the cap`() {
        assertThatThrownBy { service.updateSettings(null, 0, siteCieEnabled = true) }
            .isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `addPlacement resolves the image, numbers the ordinal and builds the url`() {
        val file = Entities.file(type = FileType.PINGER_PAINT, path = "pinger-paint/a.webp")
        every { pictures.of("pinger-paint/a.webp", FileType.PINGER_PAINT) } returns file
        every { placements.findAll() } returns listOf(placement(1, ordinal = 2))
        every { placements.save(any()) } answers { firstArg<PingerPlacement>().apply { id = 9 } }

        val view = service.addPlacement("pinger-paint/a.webp", 100, 200, 800, 600)

        assertThat(view.id).isEqualTo(9)
        assertThat(view.imageUrl).isEqualTo("/files/public/pinger-paint/a.webp")
        verify { placements.save(match { it.ordinal == 3 && it.motionEpoch == now && it.motionMode == MotionMode.STATIC }) }
    }

    @Test
    fun `addPlacement refuses a box that runs off the canvas`() {
        assertThatThrownBy { service.addPlacement("pinger-paint/a.webp", 3800, 0, 800, 10) }
            .isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `addPlacement refuses an image that is not in storage`() {
        every { pictures.of("pinger-paint/missing.webp", FileType.PINGER_PAINT) } returns null
        every { placements.findAll() } returns emptyList()

        assertThatThrownBy { service.addPlacement("pinger-paint/missing.webp", 0, 0, 10, 10) }
            .isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `movePlacement updates the box and keeps the image`() {
        every { placements.findById(5L) } returns Optional.of(placement(5))
        every { placements.save(any()) } answers { firstArg() }

        val view = service.movePlacement(5, 300, 400, 500, 600)

        assertThat(view.originX).isEqualTo(300)
        assertThat(view.width).isEqualTo(500)
        assertThat(view.imageUrl).isEqualTo("/files/public/pinger-paint/a.webp")
    }

    @Test
    fun `movePlacement restarts the motion from the new box`() {
        val moving =
            placement(5).apply {
                motionMode = MotionMode.BOUNCE
                motionVx = 100.0
            }
        every { placements.findById(5L) } returns Optional.of(moving)
        every { placements.save(any()) } answers { firstArg() }

        val view = service.movePlacement(5, 300, 400, 500, 600)

        assertThat(view.motionEpoch).isEqualTo(now)
        assertThat(view.motion).isEqualTo(PlacementMotion(MotionMode.BOUNCE, 100.0, 0.0))
    }

    @Test
    fun `setMotion re-bases the box to where it is now, so it does not jump`() {
        // Bouncing at 50 px/s right and 20 px/s down for the 10 s since the epoch.
        val moving =
            placement(5).apply {
                motionMode = MotionMode.BOUNCE
                motionVx = 50.0
                motionVy = 20.0
            }
        every { placements.findById(5L) } returns Optional.of(moving)
        every { placements.save(any()) } answers { firstArg() }

        val view = service.setMotion(5, MotionMode.BOUNCE, -300.0, 10.0)

        assertThat(view.originX).isEqualTo(600)
        assertThat(view.originY).isEqualTo(400)
        assertThat(view.motionEpoch).isEqualTo(now)
        assertThat(view.motion).isEqualTo(PlacementMotion(MotionMode.BOUNCE, -300.0, 10.0))
    }

    @Test
    fun `setMotion to static freezes the box where it is and drops the speed`() {
        val moving =
            placement(5).apply {
                motionMode = MotionMode.BOUNCE
                motionVx = -50.0
            }
        every { placements.findById(5L) } returns Optional.of(moving)
        every { placements.save(any()) } answers { firstArg() }

        val view = service.setMotion(5, MotionMode.STATIC, 300.0, 300.0)

        // 100 - 500 reflects off the left edge to 400.
        assertThat(view.originX).isEqualTo(400)
        assertThat(view.originY).isEqualTo(200)
        assertThat(view.motion).isEqualTo(PlacementMotion(MotionMode.STATIC, 0.0, 0.0))
    }

    @Test
    fun `setMotion from static starts from the origin`() {
        every { placements.findById(5L) } returns Optional.of(placement(5))
        every { placements.save(any()) } answers { firstArg() }

        val view = service.setMotion(5, MotionMode.BOUNCE, 2000.0, -2000.0)

        assertThat(view.originX).isEqualTo(100)
        assertThat(view.originY).isEqualTo(200)
    }

    @Test
    fun `setMotion refuses a speed over the cap or not a number`() {
        assertThatThrownBy { service.setMotion(5, MotionMode.BOUNCE, 2000.1, 0.0) }
            .isInstanceOf(ResponseStatusException::class.java)
        assertThatThrownBy { service.setMotion(5, MotionMode.BOUNCE, 0.0, -2001.0) }
            .isInstanceOf(ResponseStatusException::class.java)
        assertThatThrownBy { service.setMotion(5, MotionMode.BOUNCE, Double.NaN, 0.0) }
            .isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `setMotion refuses an unknown placement`() {
        every { placements.findById(404L) } returns Optional.empty()

        assertThatThrownBy { service.setMotion(404, MotionMode.BOUNCE, 1.0, 1.0) }
            .isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `movePlacement refuses a box that runs off the canvas`() {
        assertThatThrownBy { service.movePlacement(5, 0, 2100, 10, 800) }
            .isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `movePlacement refuses an unknown placement`() {
        every { placements.findById(404L) } returns Optional.empty()

        assertThatThrownBy { service.movePlacement(404, 0, 0, 10, 10) }
            .isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `removePlacement deletes an existing placement`() {
        every { placements.existsById(5L) } returns true

        service.removePlacement(5)

        verify { placements.deleteById(5L) }
    }

    @Test
    fun `removePlacement refuses an unknown placement`() {
        every { placements.existsById(404L) } returns false

        assertThatThrownBy { service.removePlacement(404) }
            .isInstanceOf(ResponseStatusException::class.java)
    }
}
