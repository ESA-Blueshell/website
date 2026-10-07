package net.blueshell.api.pinger.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PingerReportRequestTest {
    @Test
    fun `a report carries presence, rate and the client's own counts`() {
        val request = PingerReportRequest(online = true, pps = 128, sent = 500, errors = 3)

        assertThat(request.online).isTrue()
        assertThat(request.pps).isEqualTo(128)
        assertThat(request.sent).isEqualTo(500)
        assertThat(request.errors).isEqualTo(3)
    }
}
