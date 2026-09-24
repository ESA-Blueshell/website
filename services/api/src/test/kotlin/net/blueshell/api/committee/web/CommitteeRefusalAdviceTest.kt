package net.blueshell.api.committee.web

import net.blueshell.api.committee.api.CommitteeAddressTaken
import net.blueshell.api.committee.api.UnknownCommitteeAddress
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest

class CommitteeRefusalAdviceTest {
    @Test
    fun `answers a refusal with its code, its facts and the trace it happened under`() {
        MDC.put("traceId", "abc")
        try {
            val request = MockHttpServletRequest("PUT", "/committees/1")
            val problem = CommitteeRefusalAdvice().handleRefusal(CommitteeAddressTaken("Board", "board"), request)

            assertThat(problem.status).isEqualTo(HttpStatus.CONFLICT.value())
            assertThat(problem.instance.toString()).isEqualTo("/committees/1")
            assertThat(problem.properties).containsEntry("code", "CommitteeAddressTaken")
                .containsEntry("committeeName", "Board")
                .containsEntry("address", "board")
                .containsEntry("traceId", "abc")
        } finally {
            MDC.remove("traceId")
        }
    }

    @Test
    fun `answers an address nobody holds as not found, with no trace where there is none`() {
        val request = MockHttpServletRequest("GET", "/committees/address/gone")
        val problem = CommitteeRefusalAdvice().handleRefusal(UnknownCommitteeAddress("gone"), request)

        assertThat(problem.status).isEqualTo(HttpStatus.NOT_FOUND.value())
        assertThat(problem.properties).doesNotContainKey("traceId")
    }
}
