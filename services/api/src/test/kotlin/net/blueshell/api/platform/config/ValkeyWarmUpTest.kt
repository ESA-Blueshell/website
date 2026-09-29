package net.blueshell.api.platform.config

import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.connection.RedisConnection
import org.springframework.data.redis.connection.RedisConnectionFactory
import java.time.Duration

class ValkeyWarmUpTest {
    private val connection = mock<RedisConnection> { on { ping() } doReturn "PONG" }
    private val connections = mock<RedisConnectionFactory>()

    @Test
    fun `the connection is opened and pinged before the pod takes traffic`() {
        whenever(connections.connection).doReturn(connection)

        ValkeyWarmUp(connections).onReady()

        verify(connection).ping()
        verify(connection).close()
    }

    @Test
    fun `a setup that outlasts the timeout is tried again, so no request pays for it`() {
        whenever(connections.connection)
            .doThrow(RedisConnectionFailureException("Connection initialization timed out after 500 millisecond(s)"))
            .doReturn(connection)

        ValkeyWarmUp(connections, Duration.ZERO).onReady()

        verify(connections, times(2)).connection
        verify(connection).ping()
    }

    @Test
    fun `a Valkey that never answers does not stop the api coming up`() {
        whenever(connections.connection).doThrow(RedisConnectionFailureException("Unable to connect to Redis"))

        ValkeyWarmUp(connections, Duration.ZERO).onReady()

        verify(connections, times(5)).connection
    }
}
