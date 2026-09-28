package net.blueshell.api.event.web

import net.blueshell.api.event.persistence.Event
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.Instant

class EventResponseMappingsTest {
    @Test
    fun `says whether the event awaits re-approval`() {
        fun said(awaiting: Boolean) =
            Event(committee = mock(), title = "LAN", startTime = Instant.EPOCH, endTime = Instant.EPOCH)
                .apply {
                    id = 7
                    createdAt = Instant.EPOCH
                    updatedAt = Instant.EPOCH
                    awaitingReapproval = awaiting
                }.asResponse()
                .awaitingReapproval

        assertThat(said(true)).isTrue()
        assertThat(said(false)).isFalse()
    }
}
