package net.blueshell.api.platform.config

import net.blueshell.api.security.AuthTokenCookieService
import net.blueshell.api.security.CookieFlags
import net.blueshell.api.security.JwtAuthFilter
import net.blueshell.api.security.JwtAuthenticationEntryPoint
import net.blueshell.api.security.PublicAuthRateLimitFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.beans.factory.ObjectProvider
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.session.web.http.CookieSerializer.CookieValue
import java.time.Duration

/**
 * The three cookies this application sets -- auth, session and CSRF -- resolve their
 * `Secure` flag from one rule, and a browser holds them to it: a `Secure` cookie is
 * rejected outright on an insecure origin, with `localhost` and `127.0.0.1` the only
 * exemptions. Development is reached from a LAN address as well, so its cookies ask
 * for `Lax` and arrive without `Secure`. Production asks for `None`, which no browser
 * honours without `Secure`, so the flag follows regardless of what https is set to.
 */
class CookieFlagsTest {

    /**
     * Each cookie is read through its own authoritative accessor rather than through
     * header text: the auth and session cookies are written with `addHeader`, while
     * `CookieCsrfTokenRepository` calls `addCookie`, and SameSite rides on a `Cookie`
     * as an attribute rather than a field.
     */
    private data class Flags(val sameSite: String?, val secure: Boolean)

    private fun fromHeader(setCookie: String?): Flags {
        val attributes = setCookie.orEmpty().split(";").map { it.trim() }
        return Flags(
            sameSite = attributes.firstOrNull { it.startsWith("SameSite=") }?.removePrefix("SameSite="),
            secure = attributes.any { it.equals("Secure", ignoreCase = true) },
        )
    }

    private fun authCookie(sameSite: String, requireHttps: Boolean): Flags {
        val response = MockHttpServletResponse()
        AuthTokenCookieService(
            cookieName = "BSH_AUTH",
            cookiePath = "/",
            sameSite = sameSite,
            cookieDomain = "",
            requireHttps = requireHttps,
        ).writeAuthCookie(response, "token", 3_600_000)
        return fromHeader(response.getHeader("Set-Cookie"))
    }

    private fun sessionCookie(sameSite: String, requireHttps: Boolean): Flags {
        val response = MockHttpServletResponse()
        SessionConfig(
            cookieName = "SESSION",
            cookieDomain = "",
            sameSite = sameSite,
            requireHttps = requireHttps,
            sessionTimeout = Duration.ofDays(30),
        ).cookieSerializer().writeCookieValue(
            CookieValue(MockHttpServletRequest().apply { isSecure = requireHttps }, response, "abc123")
        )
        return fromHeader(response.getHeader("Set-Cookie"))
    }

    private fun csrfCookie(sameSite: String, requireHttps: Boolean): Flags {
        val repository = SecurityConfig(
            authenticationEntryPoint = mock<JwtAuthenticationEntryPoint>(),
            jwtAuthFilter = mock<JwtAuthFilter>(),
            publicAuthRateLimitFilterProvider = mock<ObjectProvider<PublicAuthRateLimitFilter>>(),
            securityCorsProperties = SecurityCorsProperties(),
            openApiPublicEnabled = false,
            requireHttps = requireHttps,
            csrfCookieSameSite = sameSite,
        ).csrfTokenRepository()

        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        repository.saveToken(repository.generateToken(request), request, response)

        val cookie = response.cookies.single { it.name == "XSRF-TOKEN" }
        return Flags(sameSite = cookie.getAttribute("SameSite"), secure = cookie.secure)
    }

    @Test
    fun `a development cookie asks for Lax and arrives without Secure, so a LAN address keeps it`() {
        val development = mapOf(
            "auth" to authCookie(sameSite = "Lax", requireHttps = false),
            "session" to sessionCookie(sameSite = "Lax", requireHttps = false),
            "csrf" to csrfCookie(sameSite = "Lax", requireHttps = false),
        )

        assertThat(development).allSatisfy { name, flags ->
            assertThat(flags).describedAs(name).isEqualTo(Flags(sameSite = "Lax", secure = false))
        }
    }

    @Test
    fun `the defaults ask for None and are Secure, which is what production ships`() {
        val production = mapOf(
            "auth" to authCookie(sameSite = "None", requireHttps = true),
            "session" to sessionCookie(sameSite = "None", requireHttps = true),
            "csrf" to csrfCookie(sameSite = "None", requireHttps = true),
        )

        assertThat(production).allSatisfy { name, flags ->
            assertThat(flags).describedAs(name).isEqualTo(Flags(sameSite = "None", secure = true))
        }
    }

    @Test
    fun `a cookie asking for None is Secure even where https is not required`() {
        // The trap this rule exists to close: None without Secure is dropped by every
        // browser, so a misconfigured development profile would lose the cookie silently.
        assertThat(CookieFlags.secure(requireHttps = false, sameSite = "None")).isTrue()
        assertThat(CookieFlags.secure(requireHttps = false, sameSite = "none")).isTrue()
        assertThat(CookieFlags.secure(requireHttps = false, sameSite = "Lax")).isFalse()
        assertThat(CookieFlags.secure(requireHttps = true, sameSite = "Lax")).isTrue()
    }

    @Test
    fun `an unset same-site falls back to None`() {
        assertThat(CookieFlags.sameSite("")).isEqualTo("None")
        assertThat(CookieFlags.sameSite("  ")).isEqualTo("None")
        assertThat(CookieFlags.sameSite(" Lax ")).isEqualTo("Lax")
    }
}
