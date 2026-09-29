package net.blueshell.api.platform.config

import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.util.Credentials
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.vault.core.lease.SecretLeaseContainer
import org.springframework.vault.core.lease.domain.Lease
import org.springframework.vault.core.lease.domain.RequestedSecret
import org.springframework.vault.core.lease.event.LeaseListener
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent
import java.time.Duration

class DatabaseLoginRotationTest {
    private val container: SecretLeaseContainer = mock()
    private val pool = HikariDataSource().apply { credentials = Credentials.of("boot", "boot-password") }

    private fun rotation(): LeaseListener {
        DatabaseLoginRotation(container, pool, "database", "api")
        val listener = argumentCaptor<LeaseListener>()
        verify(container).addLeaseListener(listener.capture())
        verify(container).addRequestedSecret(RequestedSecret.rotating("database/creds/api"))
        return listener.firstValue
    }

    private fun lease(
        path: String,
        username: String,
    ) = SecretLeaseCreatedEvent(
        RequestedSecret.rotating(path),
        Lease.of("$path/lease", Duration.ofHours(1), true),
        mapOf("username" to username, "password" to "$username-password"),
    )

    @Test
    fun `a new lease on the role becomes the pool's login`() {
        rotation().onLeaseEvent(lease("database/creds/api", "v-rotated"))

        assertThat(pool.credentials.username).isEqualTo("v-rotated")
        assertThat(pool.credentials.password).isEqualTo("v-rotated-password")
    }

    @Test
    fun `a lease on any other path leaves the pool's login alone`() {
        rotation().onLeaseEvent(lease("secret/api", "someone-else"))

        assertThat(pool.credentials.username).isEqualTo("boot")
    }
}
