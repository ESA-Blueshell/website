package net.blueshell.api.shared.refusal

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.MDC
import org.springframework.http.ProblemDetail
import java.net.URI

/** A refusal as the problem the client reads: the summary as `detail`, the code and each fact as properties. */
fun Refusal.asProblem(request: HttpServletRequest): ProblemDetail {
    val problem = ProblemDetail.forStatusAndDetail(status, summary)
    problem.type = URI.create("about:blank")
    problem.instance = URI.create(request.requestURI)
    problem.setProperty("code", code)
    facts.forEach { (name, value) -> problem.setProperty(name, value) }
    MDC.get("traceId")?.let { problem.setProperty("traceId", it) }
    return problem
}
