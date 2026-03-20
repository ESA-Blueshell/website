package net.blueshell.api.platform.integration.queue

import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.command.CommandBus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.CompletableFuture

/**
 * Dispatches commands asynchronously on the shared task executor thread pool.
 *
 * - Fire-and-forget: drop the returned future; exceptions are logged automatically.
 * - Awaitable: call .join() or .get() on the future to block until completion.
 * - Security context is propagated to the worker thread.
 * - When called inside a transaction, submission is deferred to afterCommit so
 *   the worker always observes committed state.
 *
 * @deprecated Prefer [TrackedJobDispatcher] for reliable async work (persistence + retry).
 * Use this only for truly volatile fire-and-forget work where no retry or audit trail is needed.
 */
@Deprecated(
    "Prefer TrackedJobDispatcher for reliable async work (persistence + retry). " +
    "Use this only for truly volatile fire-and-forget work where no retry or audit trail is needed."
)
@Component
class AsyncCommandDispatcher(
    private val commandBus: CommandBus,
    @field:Qualifier("taskExecutor") private val taskExecutor: TaskExecutor
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun <R, C : Command<R>> dispatch(command: C): CompletableFuture<R> {
        val callerContext: SecurityContext = SecurityContextHolder.getContext()
        return if (TransactionSynchronizationManager.isSynchronizationActive()) {
            submitAfterCommit(command, callerContext)
        } else {
            submitNow(command, callerContext)
        }
    }

    private fun <R, C : Command<R>> submitNow(
        command: C,
        callerContext: SecurityContext
    ): CompletableFuture<R> {
        val future = CompletableFuture<R>()
        taskExecutor.execute { executeInWorker(command, callerContext, future) }
        return future
    }

    private fun <R, C : Command<R>> submitAfterCommit(
        command: C,
        callerContext: SecurityContext
    ): CompletableFuture<R> {
        val future = CompletableFuture<R>()
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                taskExecutor.execute { executeInWorker(command, callerContext, future) }
            }
        })
        return future
    }

    private fun <R, C : Command<R>> executeInWorker(
        command: C,
        callerContext: SecurityContext,
        future: CompletableFuture<R>
    ) {
        val previousContext = SecurityContextHolder.getContext()
        SecurityContextHolder.setContext(callerContext)
        try {
            future.complete(commandBus.dispatch(command))
        } catch (ex: Exception) {
            logger.error(
                "Async command dispatch failed for [{}]: {}",
                command::class.simpleName, ex.message, ex
            )
            future.completeExceptionally(ex)
        } finally {
            SecurityContextHolder.setContext(previousContext)
        }
    }
}
