package net.blueshell.api.infrastructure.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository

/**
 * Pure unit tests for [JwtAuthFilter]'s credential resolution. The previous
 * implementation short-circuited on Authorization: Bearer — an opaque
 * third-party bearer (e.g. Stalwart webadmin's own OAuth token) sent
 * alongside a valid BSH_AUTH cookie would leave the request anonymous.
 * These tests pin the new contract: every available credential is tried
 * in order and the first one that validates wins.
 */
class JwtAuthFilterTest {

    private val jwtTokenUtil: JwtTokenUtil = mock()
    private val jwtRevocationService: JwtRevocationService = mock()
    private val userService: UserService = mock()
    private val authTokenCookieService: AuthTokenCookieService = mock()
    private val securityContextRepository: SecurityContextRepository = mock()

    private lateinit var filter: JwtAuthFilter

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        filter = JwtAuthFilter(
            jwtTokenUtil,
            jwtRevocationService,
            userService,
            authTokenCookieService,
            securityContextRepository,
        )
    }

    @Test
    fun `cookie only and valid - principal set from cookie`() {
        val request: HttpServletRequest = mock()
        val response: HttpServletResponse = mock()
        val chain: FilterChain = mock()
        whenever(request.getHeader("Authorization")).thenReturn(null)
        whenever(authTokenCookieService.resolveToken(eq(request))).thenReturn(COOKIE_TOKEN)
        whenever(jwtTokenUtil.parseAndValidate(eq(COOKIE_TOKEN))).thenReturn(valid("user-cookie"))
        whenever(jwtRevocationService.isRevoked(eq("jti-c"))).thenReturn(false)
        whenever(userService.loadUserPrincipalByUsername(eq("user-cookie"))).thenReturn(principal("user-cookie"))

        filter.doFilter(request, response, chain)

        assertThat(SecurityContextHolder.getContext().authentication?.name).isEqualTo("user-cookie")
        verify(chain).doFilter(eq(request), eq(response))
    }

    @Test
    fun `authorization only and valid - principal set from header`() {
        val request: HttpServletRequest = mock()
        val response: HttpServletResponse = mock()
        val chain: FilterChain = mock()
        whenever(request.getHeader("Authorization")).thenReturn("Bearer $HEADER_TOKEN")
        whenever(authTokenCookieService.resolveToken(eq(request))).thenReturn(null)
        whenever(jwtTokenUtil.parseAndValidate(eq(HEADER_TOKEN))).thenReturn(valid("user-bearer"))
        whenever(jwtRevocationService.isRevoked(eq("jti-c"))).thenReturn(false)
        whenever(userService.loadUserPrincipalByUsername(eq("user-bearer"))).thenReturn(principal("user-bearer"))

        filter.doFilter(request, response, chain)

        assertThat(SecurityContextHolder.getContext().authentication?.name).isEqualTo("user-bearer")
    }

    @Test
    fun `invalid bearer plus valid cookie - falls back to cookie (the Stalwart case)`() {
        val request: HttpServletRequest = mock()
        val response: HttpServletResponse = mock()
        val chain: FilterChain = mock()
        whenever(request.getHeader("Authorization")).thenReturn("Bearer $OPAQUE_THIRD_PARTY")
        whenever(authTokenCookieService.resolveToken(eq(request))).thenReturn(COOKIE_TOKEN)
        whenever(jwtTokenUtil.parseAndValidate(eq(OPAQUE_THIRD_PARTY))).thenReturn(
            invalid()
        )
        whenever(jwtTokenUtil.parseAndValidate(eq(COOKIE_TOKEN))).thenReturn(valid("user-cookie"))
        whenever(jwtRevocationService.isRevoked(eq("jti-c"))).thenReturn(false)
        whenever(userService.loadUserPrincipalByUsername(eq("user-cookie"))).thenReturn(principal("user-cookie"))

        filter.doFilter(request, response, chain)

        assertThat(SecurityContextHolder.getContext().authentication?.name)
            .withFailMessage("Cookie must win when the Authorization header is unparseable")
            .isEqualTo("user-cookie")
        // Both candidates should have been tried.
        verify(jwtTokenUtil).parseAndValidate(eq(OPAQUE_THIRD_PARTY))
        verify(jwtTokenUtil).parseAndValidate(eq(COOKIE_TOKEN))
    }

    @Test
    fun `both invalid - request stays anonymous`() {
        val request: HttpServletRequest = mock()
        val response: HttpServletResponse = mock()
        val chain: FilterChain = mock()
        whenever(request.getHeader("Authorization")).thenReturn("Bearer $OPAQUE_THIRD_PARTY")
        whenever(authTokenCookieService.resolveToken(eq(request))).thenReturn(COOKIE_TOKEN)
        whenever(jwtTokenUtil.parseAndValidate(any())).thenReturn(invalid())

        filter.doFilter(request, response, chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        verify(chain).doFilter(eq(request), eq(response))
        verify(userService, never()).loadUserPrincipalByUsername(anyOrNull())
    }

    @Test
    fun `both valid - authorization wins over cookie`() {
        val request: HttpServletRequest = mock()
        val response: HttpServletResponse = mock()
        val chain: FilterChain = mock()
        whenever(request.getHeader("Authorization")).thenReturn("Bearer $HEADER_TOKEN")
        whenever(authTokenCookieService.resolveToken(eq(request))).thenReturn(COOKIE_TOKEN)
        whenever(jwtTokenUtil.parseAndValidate(eq(HEADER_TOKEN))).thenReturn(valid("user-bearer"))
        whenever(jwtTokenUtil.parseAndValidate(eq(COOKIE_TOKEN))).thenReturn(valid("user-cookie"))
        whenever(jwtRevocationService.isRevoked(eq("jti-c"))).thenReturn(false)
        whenever(userService.loadUserPrincipalByUsername(eq("user-bearer"))).thenReturn(principal("user-bearer"))

        filter.doFilter(request, response, chain)

        assertThat(SecurityContextHolder.getContext().authentication?.name).isEqualTo("user-bearer")
        // Cookie shouldn't even have been parsed — the bearer validated first.
        verify(jwtTokenUtil, never()).parseAndValidate(eq(COOKIE_TOKEN))
    }

    @Test
    fun `valid token but revoked - request stays anonymous`() {
        val request: HttpServletRequest = mock()
        val response: HttpServletResponse = mock()
        val chain: FilterChain = mock()
        whenever(request.getHeader("Authorization")).thenReturn(null)
        whenever(authTokenCookieService.resolveToken(eq(request))).thenReturn(COOKIE_TOKEN)
        whenever(jwtTokenUtil.parseAndValidate(eq(COOKIE_TOKEN))).thenReturn(valid("user-cookie"))
        whenever(jwtRevocationService.isRevoked(eq("jti-c"))).thenReturn(true)

        filter.doFilter(request, response, chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        verify(userService, never()).loadUserPrincipalByUsername(anyOrNull())
    }

    @Test
    fun `valid token saves SecurityContext via the repository`() {
        val request: HttpServletRequest = mock()
        val response: HttpServletResponse = mock()
        val chain: FilterChain = mock()
        whenever(request.getHeader("Authorization")).thenReturn(null)
        whenever(authTokenCookieService.resolveToken(eq(request))).thenReturn(COOKIE_TOKEN)
        whenever(jwtTokenUtil.parseAndValidate(eq(COOKIE_TOKEN))).thenReturn(valid("user-cookie"))
        whenever(jwtRevocationService.isRevoked(eq("jti-c"))).thenReturn(false)
        whenever(userService.loadUserPrincipalByUsername(eq("user-cookie"))).thenReturn(principal("user-cookie"))

        filter.doFilter(request, response, chain)

        val captor = argumentCaptor<SecurityContext>()
        verify(securityContextRepository).saveContext(captor.capture(), eq(request), eq(response))
        assertThat(captor.firstValue.authentication).isInstanceOf(UsernamePasswordAuthenticationToken::class.java)
        assertThat(captor.firstValue.authentication?.name).isEqualTo("user-cookie")
    }

    private fun valid(username: String) =
        JwtTokenUtil.JwtValidationResult(username = username, jti = "jti-c", expired = false, error = null)

    private fun invalid() =
        JwtTokenUtil.JwtValidationResult(username = null, jti = null, expired = false, error = RuntimeException("bad token"))

    private fun principal(username: String): UserPrincipal = UserPrincipal(
        id = 1L,
        usernameValue = username,
        passwordValue = "",
        enabledValue = true,
        roles = setOf(Role.MEMBER),
        addressId = null,
        personDetailsId = null,
    )

    companion object {
        private const val COOKIE_TOKEN = "cookie.jwt.value"
        private const val HEADER_TOKEN = "header.jwt.value"
        private const val OPAQUE_THIRD_PARTY = "QpT8jgss9DY3YS2YsIZrc4mLpgvEETjMEMDn+zR4gS+oUxl5"
    }
}
