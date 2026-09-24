package net.blueshell.api.game.web

import jakarta.servlet.http.HttpServletRequest
import net.blueshell.api.game.api.GameRefusal
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

/**
 * A refused game write, as a code and its facts. See ADR-026.
 *
 * Raised as well from the esports routes that edit a game, which is why the codes are the ones
 * `esports/refusals.ts` already reads.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class GameRefusalAdvice {
    @ExceptionHandler(GameRefusal::class)
    fun handleRefusal(
        ex: GameRefusal,
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
