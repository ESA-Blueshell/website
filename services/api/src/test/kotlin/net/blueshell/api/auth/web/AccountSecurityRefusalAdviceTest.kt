package net.blueshell.api.auth.web

import net.blueshell.api.auth.domain.WrongCode
import net.blueshell.api.security.StepUpRequiredException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.LockedException

class AccountSecurityRefusalAdviceTest {
    private val advice = AccountSecurityRefusalAdvice()
    private val request = MockHttpServletRequest("POST", "/auth/two-factor")

    @Test
    fun `a refusal answers its code, its facts and the trace it happened in`() {
        MDC.put("traceId", "t-1")
        val problem = advice.handleRefusal(WrongCode(2), request)
        MDC.remove("traceId")

        assertThat(problem.status).isEqualTo(401)
        assertThat(problem.properties).containsEntry("code", "WrongCode").containsEntry("triesLeft", 2).containsEntry("traceId", "t-1")
        assertThat(problem.instance.toString()).isEqualTo("/auth/two-factor")
        assertThat(advice.handleRefusal(WrongCode(null), request).properties).doesNotContainKey("triesLeft")
    }

    @Test
    fun `a missing step-up and a locked account read as their own codes`() {
        val stepUp = advice.handleStepUp(StepUpRequiredException(), request)
        val locked = advice.handleLocked(LockedException("locked"), request)

        assertThat(stepUp.status).isEqualTo(403)
        assertThat(stepUp.properties).containsEntry("code", "StepUpRequired")
        assertThat(stepUp.instance.toString()).isEqualTo("/auth/two-factor")
        assertThat(locked.status).isEqualTo(403)
        assertThat(locked.properties).containsEntry("code", "AccountLocked")
    }
}
