package net.blueshell.api.platform.integration.queue

import jakarta.validation.ConstraintViolationException
import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.job.NonRetryableJobException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import tools.jackson.databind.ObjectMapper

/**
 * Base class for job handlers that execute a domain Command through the CommandBus.
 *
 * Responsibilities:
 * - Deserializes the JSON payload back to the typed Command (via AbstractJsonJobHandler)
 * - Enriches MDC with commandType and jobExecutionId for structured logging
 * - Logs logical state transitions: DISPATCHING → SUCCESS or FAILED
 * - Classifies ConstraintViolationException as non-retryable (marks job DEAD)
 * - Re-throws all other exceptions so JobExecutor can apply retry policy
 * - Provides onSuccess / onFailure hooks for subclass-specific side-effects
 *
 * Minimal concrete handler:
 *
 *   @Component
 *   class MyCommandJobHandler(
 *       objectMapper: ObjectMapper,
 *       commandBus: CommandBus,
 *   ) : AbstractCommandJobHandler<MyCommand, MyResult>(
 *       objectMapper, MyCommand::class.java, commandBus
 *   ) {
 *       override val jobType = MyCommandJob.type
 *   }
 */
abstract class AbstractCommandJobHandler<C : Command<R>, R>(
    objectMapper: ObjectMapper,
    commandType: Class<C>,
    private val commandBus: CommandBus,
) : AbstractJsonJobHandler<C>(objectMapper, commandType) {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun handlePayload(command: C) {
        val commandName = command::class.simpleName ?: "UnknownCommand"
        val execId = currentExecutionId?.toString() ?: "none"

        MDC.put("commandType", commandName)
        MDC.put("jobExecutionId", execId)
        try {
            logger.debug("Dispatching {} [executionId={}]", commandName, execId)
            val result = commandBus.dispatch(command)
            logger.info("Command {} completed [executionId={}]", commandName, execId)
            onSuccess(command, result)
        } catch (ex: ConstraintViolationException) {
            val violations = ex.constraintViolations.joinToString("; ") {
                "${it.propertyPath}: ${it.message}"
            }
            logger.warn(
                "Command {} rejected — validation failed [executionId={}]: {}",
                commandName, execId, violations
            )
            throw NonRetryableJobException(
                "Command validation failed for $commandName: $violations", ex
            )
        } catch (ex: Exception) {
            logger.error(
                "Command {} failed [executionId={}]: {}",
                commandName, execId, ex.message, ex
            )
            onFailure(command, ex)
            throw ex
        } finally {
            MDC.remove("commandType")
            MDC.remove("jobExecutionId")
        }
    }

    /** Called after a successful dispatch. Result is available; override for side-effects. */
    protected open fun onSuccess(command: C, result: R) {}

    /** Called before re-throwing a non-validation exception. Override for cleanup or alerting. */
    protected open fun onFailure(command: C, ex: Exception) {}
}
