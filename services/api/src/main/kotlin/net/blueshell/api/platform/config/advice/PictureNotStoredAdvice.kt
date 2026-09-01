package net.blueshell.api.platform.config.advice

import jakarta.servlet.http.HttpServletRequest
import net.blueshell.api.file.api.PictureNotStored
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

/**
 * A save that named a picture nobody stored, as a code and no sentence. See ADR-026.
 *
 * Here rather than in a module's own refusal advice because the picture a save points at is
 * resolved the same way on every page that takes an upload, so the answer is the same too and
 * a second copy of it would be a second copy to keep in step.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class PictureNotStoredAdvice {

    @ExceptionHandler(PictureNotStored::class)
    fun handle(request: HttpServletRequest): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, PictureNotStored.SUMMARY)
        problem.type = URI.create("about:blank")
        problem.instance = URI.create(request.requestURI)
        problem.setProperty("code", "PictureNotStored")
        MDC.get("traceId")?.let { problem.setProperty("traceId", it) }
        return problem
    }
}
