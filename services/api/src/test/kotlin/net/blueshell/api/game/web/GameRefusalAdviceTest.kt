package net.blueshell.api.game.web

import net.blueshell.api.game.api.AddressTaken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest

class GameRefusalAdviceTest {
    @Test
    fun `answers a refusal with its code, its facts and the trace it happened under`() {
        MDC.put("traceId", "abc")
        try {
            val problem = GameRefusalAdvice().handleRefusal(AddressTaken("Chess", "chess"), MockHttpServletRequest("PUT", "/games/CHESS"))

            assertThat(problem.status).isEqualTo(HttpStatus.CONFLICT.value())
            assertThat(problem.instance.toString()).isEqualTo("/games/CHESS")
            assertThat(problem.properties).containsEntry("code", "AddressTaken")
                .containsEntry("gameName", "Chess")
                .containsEntry("address", "chess")
                .containsEntry("traceId", "abc")
        } finally {
            MDC.remove("traceId")
        }
    }

    @Test
    fun `leaves the trace out when there is none`() {
        val problem = GameRefusalAdvice().handleRefusal(AddressTaken("Chess", "chess"), MockHttpServletRequest("PUT", "/games/CHESS"))

        assertThat(problem.properties).doesNotContainKey("traceId")
    }
}
