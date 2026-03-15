package net.blueshell.api.platform.integration.queue

import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.command.CommandBus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
class AsyncCommandDispatcherTest {

    private val commandBus: CommandBus = mock()
    private val syncExecutor = SyncTaskExecutor()
    private val dispatcher = AsyncCommandDispatcher(commandBus, syncExecutor)

    private data class PingCommand(val value: String) : Command<String>

    @BeforeEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun cleanup() {
        SecurityContextHolder.clearContext()
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `dispatch completes future with handler result`() {
        val command = PingCommand("hello")
        whenever(commandBus.dispatch(command)).thenReturn("pong")

        val future = dispatcher.dispatch(command)

        assertThat(future.get(1, TimeUnit.SECONDS)).isEqualTo("pong")
    }

    @Test
    fun `dispatch on failing command completes future exceptionally and logs error`() {
        val command = PingCommand("boom")
        whenever(commandBus.dispatch(command)).thenThrow(RuntimeException("dispatch error"))

        val future = dispatcher.dispatch(command)

        assertThat(future.isCompletedExceptionally).isTrue()
        val cause = runCatching { future.get() }.exceptionOrNull()
        assertThat(cause).isInstanceOf(ExecutionException::class.java)
        assertThat(cause?.cause).hasMessage("dispatch error")
    }

    @Test
    fun `dispatch propagates security context to worker`() {
        val principal = UsernamePasswordAuthenticationToken("user", "pass")
        val ctx = SecurityContextImpl(principal)
        SecurityContextHolder.setContext(ctx)

        var workerPrincipal: Any? = null
        val capturingExecutor = org.springframework.core.task.TaskExecutor { task ->
            workerPrincipal = SecurityContextHolder.getContext().authentication?.principal
            task.run()
        }
        val dispatcherWithCapture = AsyncCommandDispatcher(commandBus, capturingExecutor)
        whenever(commandBus.dispatch(any<PingCommand>())).thenReturn("ok")

        dispatcherWithCapture.dispatch(PingCommand("ctx-test")).get(1, TimeUnit.SECONDS)

        assertThat(workerPrincipal).isEqualTo("user")
    }

    @Test
    fun `dispatch inside active transaction defers to afterCommit`() {
        val command = PingCommand("tx")
        whenever(commandBus.dispatch(command)).thenReturn("committed")

        TransactionSynchronizationManager.initSynchronization()
        try {
            val future = dispatcher.dispatch(command)

            // Future should not yet be complete while synchronization is active
            assertThat(future.isDone).isFalse()

            // Simulate commit
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

            assertThat(future.get(1, TimeUnit.SECONDS)).isEqualTo("committed")
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `dispatch outside transaction submits immediately`() {
        val command = PingCommand("now")
        whenever(commandBus.dispatch(command)).thenReturn("immediate")

        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse()

        val future = dispatcher.dispatch(command)

        // SyncTaskExecutor runs inline, so future is already done
        assertThat(future.isDone).isTrue()
        assertThat(future.get()).isEqualTo("immediate")
    }

    @Test
    fun `dispatch executes on worker thread when using real thread pool`() {
        val callerThread = Thread.currentThread().name
        val threadPool = Executors.newSingleThreadExecutor()
        val threadPoolExecutor = org.springframework.core.task.TaskExecutor { threadPool.execute(it) }
        val asyncDispatcher = AsyncCommandDispatcher(commandBus, threadPoolExecutor)

        var workerThread: String? = null
        whenever(commandBus.dispatch(any<PingCommand>())).thenAnswer {
            workerThread = Thread.currentThread().name
            "result"
        }

        asyncDispatcher.dispatch(PingCommand("threaded")).get(2, TimeUnit.SECONDS)

        assertThat(workerThread).isNotEqualTo(callerThread)
        threadPool.shutdown()
    }
}
