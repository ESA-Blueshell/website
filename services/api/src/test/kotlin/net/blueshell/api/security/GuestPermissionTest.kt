package net.blueshell.api.security

import net.blueshell.api.event.domain.GuestPermission
import net.blueshell.api.event.domain.GuestService
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GuestPermissionTest {
    private val service = mock<GuestService>()
    private val evaluator = GuestPermission(service)

    @Test
    fun `guest permissions require auth entity and permission and only allow read write`() {
        val guest = Entities.guest()
        assertThat(evaluator.hasPermission(null, guest, "read")).isFalse()
        assertThat(evaluator.hasPermission(guestAuth(), null, "read")).isFalse()
        assertThat(evaluator.hasPermission(guestAuth(), guest, null)).isFalse()
        assertThat(evaluator.hasPermission(guestAuth(), guest, "read")).isTrue()
        assertThat(evaluator.hasPermission(guestAuth(), guest, "write")).isTrue()
        assertThat(evaluator.hasPermission(guestAuth(), guest, "delete")).isFalse()
    }

    @Test
    fun `hasPermissionId resolves guest via access token`() {
        val guest = Entities.guest()
        whenever(service.findByAccessToken("token-123")).thenReturn(guest)

        assertThat(evaluator.hasPermissionId(guestAuth(), null, "read")).isFalse()
        assertThat(evaluator.hasPermissionId(guestAuth(), "token-123", "read")).isTrue()
        verify(service).findByAccessToken("token-123")
    }
}
