package net.blueshell.api.esports.web

import jakarta.servlet.http.HttpServletRequest
import net.blueshell.api.esports.domain.EsportsRefusal
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class EsportsRefusalAdvice {

    @ExceptionHandler(EsportsRefusal::class)
    fun handleRefusal(ex: EsportsRefusal, request: HttpServletRequest): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(ex.status, ex.summary)
        problem.type = URI.create("about:blank")
        problem.instance = URI.create(request.requestURI)
        problem.setProperty("code", ex.code)
        ex.facts.forEach { (name, value) -> problem.setProperty(name, value) }
        MDC.get("traceId")?.let { problem.setProperty("traceId", it) }
        return problem
    }
}
