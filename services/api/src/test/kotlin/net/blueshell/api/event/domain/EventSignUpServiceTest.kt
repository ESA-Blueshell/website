package net.blueshell.api.event.domain

import net.blueshell.api.event.persistence.EventSignUpRepository
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.security.CurrentUserProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EventSignUpServiceTest {
    private val repository = mock<EventSignUpRepository>()
    private val service = EventSignUpService(repository, mock<TrackedEventPublisher>(), mock<CurrentUserProvider>())

    @Test
    fun `answers whether an account already signed up for an event`() {
        whenever(repository.existsByUser_IdAndEvent_Id(9L, 100L)).thenReturn(true)
        whenever(repository.existsByUser_IdAndEvent_Id(9L, 101L)).thenReturn(false)

        assertThat(service.existsByUserIdAndEventId(9L, 100L)).isTrue()
        assertThat(service.existsByUserIdAndEventId(9L, 101L)).isFalse()
        verify(repository).existsByUser_IdAndEvent_Id(9L, 100L)
    }
}
