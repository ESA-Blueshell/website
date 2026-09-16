package net.blueshell.api.security

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

class ValkeyRevokedJtiStoreTest {

    private val values: ValueOperations<String, String> = mock()
    private val redis: StringRedisTemplate = mock<StringRedisTemplate>().also {
        whenever(it.opsForValue()).thenReturn(values)
    }
    private val store = ValkeyRevokedJtiStore(redis)

    private val key = "${ValkeyRevokedJtiStore.KEY_PREFIX}jti-1"

    @Test
    fun `a revocation is written under the namespaced key, with the ttl it was given`() {
        store.add("jti-1", Duration.ofDays(3))

        verify(values).set(eq(key), eq("1"), eq(Duration.ofDays(3)))
    }

    @Test
    fun `a ttl of nothing writes nothing`() {
        store.add("jti-1", Duration.ZERO)
        store.add("jti-1", Duration.ofSeconds(-1))

        verify(values, never()).set(any(), any(), any<Duration>())
    }

    @Test
    fun `an unreachable valkey does not fail the sign-out`() {
        whenever(values.set(any(), any(), any<Duration>()))
            .thenThrow(RedisConnectionFailureException("down"))

        assertThatCode { store.add("jti-1", Duration.ofDays(3)) }.doesNotThrowAnyException()
    }

    @Test
    fun `an unreachable valkey reads as not revoked, rather than signing everybody out`() {
        whenever(redis.hasKey(key)).thenThrow(RedisConnectionFailureException("down"))

        assertThat(store.contains("jti-1")).isFalse()
    }

    @Test
    fun `a key that is there is revoked`() {
        whenever(redis.hasKey(key)).thenReturn(true)

        assertThat(store.contains("jti-1")).isTrue()
    }
}
