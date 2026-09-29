package net.blueshell.api.event.domain

import net.blueshell.api.event.persistence.EventBanner
import net.blueshell.api.event.persistence.EventBannerRepository
import net.blueshell.api.event.persistence.GuestRepository
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

class EventReadServicesTest {
    @Test
    fun `reads a banner, and refuses one that is not there`() {
        val repository = mock<EventBannerRepository>()
        val banner = Entities.banner(Entities.event())
        val held = mock<EventBanner.Id>()
        whenever(repository.findById(any())).thenReturn(Optional.empty())
        whenever(repository.findById(held)).thenReturn(Optional.of(banner))
        val service = EventBannerService(repository)

        assertThat(service.findById(held)).isSameAs(banner)
        assertThatThrownBy { service.findById(mock()) }.isInstanceOf(EventBannerNotFoundException::class.java)
    }

    @Test
    fun `removes a guest`() {
        val repository = mock<GuestRepository>()
        val guest = Entities.guest()

        GuestService(repository).delete(guest)

        verify(repository).delete(guest)
    }
}
