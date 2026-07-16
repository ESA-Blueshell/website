package net.blueshell.api.platform.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.session.config.SessionRepositoryCustomizer
import org.springframework.session.data.redis.RedisSessionRepository
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession
import org.springframework.session.web.http.CookieSerializer
import org.springframework.session.web.http.DefaultCookieSerializer
import java.time.Duration

/**
 * Server-side HTTP sessions stored in Valkey. The SESSION cookie outlives the
 * 24h JWT, so once authenticated the browser stays signed in for the session
 * timeout (default 30d) without re-login. Cookie domain mirrors the auth
 * cookie so the session travels to every subdomain behind Traefik forwardAuth.
 */
@Configuration
@EnableRedisHttpSession(redisNamespace = "blueshell-api")
class SessionConfig(
    @param:Value($$"${session.cookie.name:SESSION}")
    private val cookieName: String,
    @param:Value($$"${session.cookie.domain:}")
    private val cookieDomain: String,
    @param:Value($$"${session.cookie.same-site:None}")
    private val sameSite: String,
    @param:Value($$"${app.security.require-https:true}")
    private val requireHttps: Boolean,
    @param:Value($$"${session.timeout:30d}")
    private val sessionTimeout: Duration,
) {
    @Bean
    fun cookieSerializer(): CookieSerializer =
        DefaultCookieSerializer().apply {
            setCookieName(cookieName)
            setCookiePath("/")
            setSameSite(sameSite)
            setUseHttpOnlyCookie(true)
            setCookieMaxAge(sessionTimeout.seconds.toInt())
            // SameSite=None requires Secure; localhost is a secure context in dev.
            setUseSecureCookie(requireHttps || sameSite.equals("None", ignoreCase = true))
            if (cookieDomain.isNotBlank()) {
                // Spring Session's DefaultCookieSerializer rejects a leading-dot
                // domain (legacy RFC 2109) and throws on every session commit,
                // 500-ing the whole API. The auth cookie keeps its dotted value;
                // strip the dot here — RFC 6265 `Domain=esa-blueshell.nl` already
                // scopes the cookie to every subdomain.
                setDomainName(cookieDomain.removePrefix("."))
            }
        }

    @Bean
    fun redisSessionRepositoryCustomizer(): SessionRepositoryCustomizer<RedisSessionRepository> =
        SessionRepositoryCustomizer { it.setDefaultMaxInactiveInterval(sessionTimeout) }

    /**
     * Spring Session picks up a bean named `springSessionDefaultRedisSerializer`
     * to (de)serialize session attributes — including the Spring Security context
     * that holds the UserPrincipal. Using the fault-tolerant serializer means a
     * session written by a previous deploy that can no longer be deserialized is
     * dropped and rebuilt from the JWT cookie, instead of 500ing every request
     * until the 30-day session TTL lapses.
     */
    @Bean
    fun springSessionDefaultRedisSerializer(): RedisSerializer<Any> = FaultTolerantRedisSerializer()
}
