package net.blueshell.api.security

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Instant

class StepUpTest {
    private val signIns = mock<SignIns>()
    private val stepUp = StepUp(signIns)
    private val signIn = SignIn("s", 7, Instant.EPOCH, Instant.EPOCH, Browser.UNKNOWN, 0, "j", Instant.EPOCH)

    @AfterEach
    fun tearDown() = RequestContextHolder.resetRequestAttributes()

    private fun during(signIn: SignIn?) {
        val request = MockHttpServletRequest().apply { signIn?.let { setAttribute(SignInContext.ATTRIBUTE, it) } }
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
    }

    @Test
    fun `a sign-in proved within the window may go on`() {
        during(signIn)
        whenever(signIns.find("s")).thenReturn(signIn)
        whenever(signIns.steppedUpWithin(signIn, StepUp.WINDOW)).thenReturn(true)

        assertDoesNotThrow { stepUp.require() }
    }

    @Test
    fun `no sign-in, a sign-in gone, or one not proved lately is asked for a step-up`() {
        assertThrows<StepUpRequiredException> { stepUp.require() }

        during(signIn)
        assertThrows<StepUpRequiredException> { stepUp.require() }

        whenever(signIns.find("s")).thenReturn(signIn)
        assertThrows<StepUpRequiredException> { stepUp.require() }
    }
}
