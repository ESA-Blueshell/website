package net.blueshell.api.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class JwtRevocationServiceTest {

    private val lifetime = Duration.ofDays(30)

    /** The store, near enough: what went in and for how long, without a Valkey to ask. */
    private class RecordingStore : RevokedJtiStore {
        val written = mutableMapOf<String, Duration>()

        override fun add(jti: String, ttl: Duration) {
            written[jti] = ttl
        }

        override fun contains(jti: String): Boolean = written.containsKey(jti)
    }

    private val store = RecordingStore()
    private val service = JwtRevocationService("a,b , c", lifetime, store)

    @Test
    fun `reads initial revoked set from configuration string`() {
        assertThat(service.isRevoked("a")).isTrue()
        assertThat(service.isRevoked("b")).isTrue()
        assertThat(service.isRevoked("c")).isTrue()
        assertThat(service.isRevoked("d")).isFalse()
    }

    @Test
    fun `the configured list is not written to the store, which would give it an end`() {
        assertThat(store.written).isEmpty()
    }

    @Test
    fun `supports runtime revocation`() {
        assertThat(service.isRevoked("new-jti")).isFalse()

        service.revoke("new-jti")

        assertThat(service.isRevoked("new-jti")).isTrue()
    }

    @Test
    fun `a revocation lasts as long as the token had left`() {
        service.revoke("half-spent", System.currentTimeMillis() + Duration.ofDays(10).toMillis())

        assertThat(store.written["half-spent"]).isBetween(Duration.ofDays(9), Duration.ofDays(10))
    }

    @Test
    fun `a token that does not say when it expires is refused for a whole lifetime`() {
        service.revoke("no-expiry")

        assertThat(store.written["no-expiry"]).isEqualTo(lifetime)
    }

    @Test
    fun `a token already past its expiry is refused for a whole lifetime rather than for nothing`() {
        service.revoke("stale", System.currentTimeMillis() - 1_000)

        assertThat(store.written["stale"]).isEqualTo(lifetime)
    }

    @Test
    fun `a blank id is not written down`() {
        service.revoke("   ")

        assertThat(store.written).isEmpty()
        assertThat(service.isRevoked("")).isFalse()
        assertThat(service.isRevoked(null)).isFalse()
    }
}
