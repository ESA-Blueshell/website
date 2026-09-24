package net.blueshell.api.auth.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.blueshell.api.auth.domain.AuthenticationService
import net.blueshell.api.security.AuthTokenCookieService
import net.blueshell.api.security.Browser
import net.blueshell.api.security.JwtTokenUtil
import net.blueshell.api.security.SignIns
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Authentication")
class AuthenticationController(
    private val authenticationService: AuthenticationService,
    private val authTokenCookieService: AuthTokenCookieService,
    private val jwtTokenUtil: JwtTokenUtil,
    private val signIns: SignIns,
) {
    @PostMapping("/auth")
    @PermitAll
    fun authenticate(
        @Validated @RequestBody authenticationRequest: JwtRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): AuthenticationResponse {
        val signedIn =
            authenticationService.signIn(
                authenticationRequest.username,
                authenticationRequest.password,
                Browser.of(request.getHeader(HttpHeaders.USER_AGENT)),
            )
        authTokenCookieService.writeAuthCookie(response, signedIn.issued.token, signedIn.issued.cookieTtl.toMillis())
        return signedIn.signer.asResponse()
    }

    @PostMapping("/auth/logout")
    @PermitAll
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        jwtTokenUtil.read(authTokenCookieService.resolveToken(request))?.let { signIns.end(it.sid) }
        request.getSession(false)?.invalidate()
        authTokenCookieService.clearAuthCookie(response)
    }
}
