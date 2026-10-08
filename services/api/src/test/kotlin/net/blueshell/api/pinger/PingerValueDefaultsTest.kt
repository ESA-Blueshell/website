package net.blueshell.api.pinger

import net.blueshell.api.pinger.persistence.PingerPlacement
import net.blueshell.api.pinger.web.PaintSettingsRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PingerValueDefaultsTest {
    @Test
    fun `a placement with no ordinal draws first`() {
        val placement = PingerPlacement(imagePath = "pinger-paint/a.webp", originX = 0, originY = 0, width = 1, height = 1)

        assertThat(placement.ordinal).isEqualTo(0)
    }

    @Test
    fun `settings default the SiteCie painter on`() {
        val request = PaintSettingsRequest(ratePps = 1)

        assertThat(request.siteCieEnabled).isTrue()
    }
}
