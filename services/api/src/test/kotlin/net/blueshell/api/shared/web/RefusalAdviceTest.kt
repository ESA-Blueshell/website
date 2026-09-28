package net.blueshell.api.shared.web

import net.blueshell.api.shared.refusal.Refusal
import net.blueshell.api.shared.refusal.asProblem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest

class RefusalAdviceTest {
    private class Taken(
        name: String,
    ) : Refusal(HttpStatus.CONFLICT, "Taken", "That name is taken.", mapOf("name" to name))

    private class Plain : Refusal(HttpStatus.BAD_REQUEST, "Plain", "No.")

    @Test
    fun `answers any module's refusal with its summary, code, facts and trace`() {
        MDC.put("traceId", "t-9")
        try {
            val problem = RefusalAdvice().handleRefusal(Taken("chess"), MockHttpServletRequest("PUT", "/games/CHESS"))

            assertThat(problem.status).isEqualTo(409)
            assertThat(problem.detail).isEqualTo("That name is taken.")
            assertThat(problem.type.toString()).isEqualTo("about:blank")
            assertThat(problem.instance.toString()).isEqualTo("/games/CHESS")
            assertThat(problem.properties).containsEntry("code", "Taken").containsEntry("name", "chess").containsEntry("traceId", "t-9")
        } finally {
            MDC.remove("traceId")
        }
    }

    @Test
    fun `carries no facts and no trace where there are none`() {
        val problem = Plain().asProblem(MockHttpServletRequest("POST", "/x"))

        assertThat(problem.properties).containsOnlyKeys("code")
    }
}
