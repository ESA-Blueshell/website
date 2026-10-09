package net.blueshell.api.pinger

import net.blueshell.api.pinger.domain.MotionMode
import net.blueshell.api.pinger.persistence.PingerPlacement
import net.blueshell.api.pinger.web.PaintSettingsRequest
import net.blueshell.api.pinger.web.PlacementMotionRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class PingerValueDefaultsTest {
    private fun placement() =
        PingerPlacement(imagePath = "pinger-paint/a.webp", originX = 0, originY = 0, width = 1, height = 1, motionEpoch = Instant.EPOCH)

    @Test
    fun `a placement with no ordinal draws first`() {
        val placement = placement()

        assertThat(placement.ordinal).isEqualTo(0)
    }

    @Test
    fun `a placement with no motion stands still`() {
        val placement = placement()

        assertThat(placement.motionMode).isEqualTo(MotionMode.STATIC)
        assertThat(placement.motionVx).isZero()
        assertThat(placement.motionVy).isZero()
    }

    @Test
    fun `a motion request with no speed has none`() {
        val request = PlacementMotionRequest(MotionMode.BOUNCE)

        assertThat(request.vx).isZero()
        assertThat(request.vy).isZero()
    }

    @Test
    fun `settings default the SiteCie painter on`() {
        val request = PaintSettingsRequest(ratePps = 1)

        assertThat(request.siteCieEnabled).isTrue()
    }
}
