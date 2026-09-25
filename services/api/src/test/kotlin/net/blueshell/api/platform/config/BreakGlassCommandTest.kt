package net.blueshell.api.platform.config

import net.blueshell.api.auth.domain.BreakGlass
import net.blueshell.api.auth.domain.BreakGlassAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ConfigurableApplicationContext
import java.time.Duration

class BreakGlassCommandTest {
    private val context = mock<ConfigurableApplicationContext>()
    private val breakGlass = mock<BreakGlass>()
    private val exits = mutableListOf<Int>()

    private fun command() =
        BreakGlassCommand(context, breakGlass, BreakGlassAction.UNLOCK, "root", "in person", Duration.ZERO).also {
            it.exit = { code -> exits += code }
        }

    @Test
    fun `a run that goes through ends the process with 0 and closes the context`() {
        command().onReady()

        verify(breakGlass).run(BreakGlassAction.UNLOCK, "root", "in person")
        verify(context).close()
        assertThat(exits).containsExactly(0)
    }

    @Test
    fun `a refused run ends it with 1`() {
        whenever(breakGlass.run(BreakGlassAction.UNLOCK, "root", "in person")).doThrow(IllegalArgumentException("no"))

        command().onReady()

        assertThat(exits).containsExactly(1)
    }
}
