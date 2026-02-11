package net.blueshell.api.domain.auth.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import java.io.IOException
import java.io.Serial
import java.io.Serializable

@Component
class JwtAuthenticationEntryPoint : AuthenticationEntryPoint, Serializable {
    @Throws(IOException::class)
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
    }

    companion object {
        @Serial
        private val serialVersionUID = -7858869558953243875L
    }
}
