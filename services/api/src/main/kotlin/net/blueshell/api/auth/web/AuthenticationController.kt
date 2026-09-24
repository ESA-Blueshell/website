package net.blueshell.api.auth.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import net.blueshell.api.auth.domain.AuthenticationService
import net.blueshell.api.auth.domain.SignInOutcome
import net.blueshell.api.auth.domain.twofactor.Challenges
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
    private val cookies: AuthTokenCookieService,
    private val jwtTokenUtil: JwtTokenUtil,
    private val signIns: SignIns,
) {
    @PostMapping("/auth")
    @PermitAll
    fun authenticate(
        @Validated @RequestBody authenticationRequest: JwtRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): SignInAnswer {
        val outcome =
            authenticationService.signIn(
                authenticationRequest.username,
                authenticationRequest.password,
                browserOf(request),
                cookies.resolveCookie(request, TRUSTED_BROWSER_COOKIE),
            )
        return when (outcome) {
            is SignInOutcome.SignedIn -> SignInAnswer(SignInStatus.SIGNED_IN, write(outcome, response))
            is SignInOutcome.Challenged -> {
                cookies.writeCookie(response, CHALLENGE_COOKIE, outcome.challengeId, Challenges.LIFETIME.toMillis())
                SignInAnswer(SignInStatus.TWO_FACTOR_REQUIRED)
            }
        }
    }

    @PostMapping("/auth/two-factor")
    @PermitAll
    fun answerChallenge(
        @Valid @RequestBody body: TwoFactorCodeRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): SignInAnswer {
        val signedIn =
            authenticationService.answerChallenge(
                cookies.resolveCookie(request, CHALLENGE_COOKIE),
                body.code,
                browserOf(request),
                body.trustThisBrowser,
            )
        cookies.clearCookie(response, CHALLENGE_COOKIE)
        return SignInAnswer(SignInStatus.SIGNED_IN, write(signedIn, response))
    }

    @PostMapping("/recovery/two-factor/re-enrol")
    @PermitAll
    fun reenrol(
        @Valid @RequestBody body: ReenrolRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): SignInAnswer {
        val signedIn = authenticationService.reenrol(body.username, body.password, body.token, browserOf(request))
        return SignInAnswer(SignInStatus.SIGNED_IN, write(signedIn, response))
    }

    @PostMapping("/auth/logout")
    @PermitAll
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        jwtTokenUtil.read(cookies.resolveToken(request))?.let { signIns.end(it.sid) }
        request.getSession(false)?.invalidate()
        cookies.clearAuthCookie(response)
    }

    private fun write(
        signedIn: SignInOutcome.SignedIn,
        response: HttpServletResponse,
    ): AuthenticationResponse {
        cookies.writeAuthCookie(response, signedIn.issued.token, signedIn.issued.cookieTtl.toMillis())
        signedIn.trustedBrowser?.let { cookies.writeCookie(response, TRUSTED_BROWSER_COOKIE, it.cookieValue, it.ttl.toMillis()) }
        return signedIn.signer.asResponse()
    }

    private fun browserOf(request: HttpServletRequest) = Browser.of(request.getHeader(HttpHeaders.USER_AGENT))

    companion object {
        const val CHALLENGE_COOKIE = "BSH_2FA_CHALLENGE"
        const val TRUSTED_BROWSER_COOKIE = "BSH_TRUSTED_BROWSER"
    }
}
