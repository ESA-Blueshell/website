package net.blueshell.api.board.web

import jakarta.servlet.http.HttpServletRequest
import net.blueshell.api.board.domain.BoardRefusal
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

/**
 * A refused board write, as a code and its facts. See ADR-026.
 *
 * The board module's own advice rather than a cross-cutting one, because a board refusal is
 * about a board: nothing outside this module can raise one, and the facts are the board's.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class BoardRefusalAdvice {

    @ExceptionHandler(BoardRefusal::class)
    fun handleRefusal(ex: BoardRefusal, request: HttpServletRequest): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(ex.status, ex.summary)
        problem.type = URI.create("about:blank")
        problem.instance = URI.create(request.requestURI)
        problem.setProperty("code", ex.code)
        ex.facts.forEach { (name, value) -> problem.setProperty(name, value) }
        MDC.get("traceId")?.let { problem.setProperty("traceId", it) }
        return problem
    }
}
