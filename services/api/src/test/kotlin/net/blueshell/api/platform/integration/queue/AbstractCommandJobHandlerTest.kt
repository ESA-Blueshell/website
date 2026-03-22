package net.blueshell.api.platform.integration.queue

import jakarta.validation.ConstraintViolationException
import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.job.NonRetryableJobException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.MDC
import tools.jackson.databind.ObjectMapper

/**
 * Unit tests for [AbstractCommandJobHandler].
 *
 * No Spring context — instantiate directly with mocks.
 */
class AbstractCommandJobHandlerTest {

    data class TestCommand(val value: String) : Command<String>

    private val objectMapper = ObjectMapper()
    private val commandBus: CommandBus = mock()

    private var capturedSuccessCommand: TestCommand? = null
    private var capturedSuccessResult: String? = null
    private var capturedFailureCommand: TestCommand? = null
    private var capturedFailureException: Exception? = null

    private val handler = object : AbstractCommandJobHandler<TestCommand, String>(
        objectMapper, TestCommand::class.java, commandBus
    ) {
        override val jobType = "test.command"

        override fun onSuccess(command: TestCommand, result: String) {
            capturedSuccessCommand = command
            capturedSuccessResult = result
        }

        override fun onFailure(command: TestCommand, ex: Exception) {
            capturedFailureCommand = command
            capturedFailureException = ex
        }
    }

    @BeforeEach
    fun reset() {
        capturedSuccessCommand = null
        capturedSuccessResult = null
        capturedFailureCommand = null
        capturedFailureException = null
        MDC.clear()
    }

    @Test
    fun `successful dispatch calls onSuccess with command and result`() {
        val command = TestCommand("hello")
        whenever(commandBus.dispatch(command)).thenReturn("done")

        handler.handle(objectMapper.writeValueAsString(command), 42L)

        verify(commandBus).dispatch(command)
        assertThat(capturedSuccessCommand).isEqualTo(command)
        assertThat(capturedSuccessResult).isEqualTo("done")
        assertThat(capturedFailureCommand).isNull()
    }

    @Test
    fun `ConstraintViolationException is wrapped in NonRetryableJobException`() {
        val command = TestCommand("bad")
        whenever(commandBus.dispatch(command)).thenThrow(ConstraintViolationException(emptySet()))

        assertThatThrownBy { handler.handle(objectMapper.writeValueAsString(command), null) }
            .isInstanceOf(NonRetryableJobException::class.java)
            .hasMessageContaining("TestCommand")
    }

    @Test
    fun `ConstraintViolationException does not trigger onFailure`() {
        val command = TestCommand("bad")
        whenever(commandBus.dispatch(command)).thenThrow(ConstraintViolationException(emptySet()))

        runCatching { handler.handle(objectMapper.writeValueAsString(command), null) }

        assertThat(capturedFailureCommand).isNull()
    }

    @Test
    fun `runtime exception is propagated unchanged`() {
        val command = TestCommand("fail")
        val ex = RuntimeException("boom")
        whenever(commandBus.dispatch(command)).thenThrow(ex)

        assertThatThrownBy { handler.handle(objectMapper.writeValueAsString(command), null) }
            .isSameAs(ex)
    }

    @Test
    fun `runtime exception triggers onFailure with command and exception`() {
        val command = TestCommand("fail")
        val ex = RuntimeException("boom")
        whenever(commandBus.dispatch(command)).thenThrow(ex)

        runCatching { handler.handle(objectMapper.writeValueAsString(command), null) }

        assertThat(capturedFailureCommand).isEqualTo(command)
        assertThat(capturedFailureException).isSameAs(ex)
    }

    @Test
    fun `MDC is enriched with commandType and jobExecutionId during dispatch`() {
        val command = TestCommand("mdc")
        var capturedType: String? = null
        var capturedExecId: String? = null
        whenever(commandBus.dispatch(command)).thenAnswer {
            capturedType = MDC.get("commandType")
            capturedExecId = MDC.get("jobExecutionId")
            "result"
        }

        handler.handle(objectMapper.writeValueAsString(command), 99L)

        assertThat(capturedType).isEqualTo("TestCommand")
        assertThat(capturedExecId).isEqualTo("99")
    }

    @Test
    fun `MDC is cleared after successful dispatch`() {
        val command = TestCommand("mdc-ok")
        whenever(commandBus.dispatch(command)).thenReturn("result")

        handler.handle(objectMapper.writeValueAsString(command), 1L)

        assertThat(MDC.get("commandType")).isNull()
        assertThat(MDC.get("jobExecutionId")).isNull()
    }

    @Test
    fun `MDC is cleared even when an exception is thrown`() {
        val command = TestCommand("mdc-fail")
        whenever(commandBus.dispatch(command)).thenThrow(RuntimeException("fail"))

        runCatching { handler.handle(objectMapper.writeValueAsString(command), 5L) }

        assertThat(MDC.get("commandType")).isNull()
        assertThat(MDC.get("jobExecutionId")).isNull()
    }

    @Test
    fun `executionId none is used in MDC when not provided`() {
        val command = TestCommand("no-exec-id")
        var capturedExecId: String? = null
        whenever(commandBus.dispatch(command)).thenAnswer {
            capturedExecId = MDC.get("jobExecutionId")
            "result"
        }

        handler.handle(objectMapper.writeValueAsString(command), null)

        assertThat(capturedExecId).isEqualTo("none")
    }
}
