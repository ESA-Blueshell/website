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

/**
 * Reports a refused esports write as its code and the facts about it.
 *
 * One handler for every refusal, because the shape is the same one every time: `code`, then each
 * of the refusal's facts as a property of its own. Named properties rather than the `errors`
 * array a refused bulk selection answers with — that shape carries a violation per field over a
 * selection of rows, and an esports refusal is one fact about one game, one season or one team,
 * so `values[0]` and `values[1]` would name nothing.
 *
 * `detail` is the refusal's fixed summary. It interpolates nothing, so the sentence a reader
 * meets is composed in `esports/refusals.ts` and this stays readable in a log. See ADR-026.
 */
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
