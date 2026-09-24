package net.blueshell.api.committee.web

import jakarta.servlet.http.HttpServletRequest
import net.blueshell.api.committee.api.CommitteeRefusal
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

/** A refused committee write, as a code and its facts. See ADR-026. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class CommitteeRefusalAdvice {
    @ExceptionHandler(CommitteeRefusal::class)
    fun handleRefusal(
        ex: CommitteeRefusal,
        request: HttpServletRequest,
    ): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(ex.status, ex.summary)
        problem.type = URI.create("about:blank")
        problem.instance = URI.create(request.requestURI)
        problem.setProperty("code", ex.code)
        ex.facts.forEach { (name, value) -> problem.setProperty(name, value) }
        MDC.get("traceId")?.let { problem.setProperty("traceId", it) }
        return problem
    }
}
