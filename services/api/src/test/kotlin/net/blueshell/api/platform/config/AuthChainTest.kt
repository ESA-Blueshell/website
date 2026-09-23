package net.blueshell.api.platform.config

import net.blueshell.api.security.JwtAuthFilter
import net.blueshell.api.security.JwtAuthenticationEntryPoint
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.stubbing.Answer
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
import org.springframework.security.web.DefaultSecurityFilterChain
import java.lang.reflect.ParameterizedType

/**
 * The auth chain's rules, read off a stand-in [HttpSecurity] that hands each DSL block a stand-in
 * configurer. Nothing is enforced here, which the integration suite covers; this pins what the chain
 * asks for without a Spring context.
 */
class AuthChainTest {
    private val registry: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry =
        mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)

    /* Runs every Customizer it is given, and keeps the DSL chaining on itself. */
    private val dsl =
        Answer { call: InvocationOnMock ->
            call.arguments.forEachIndexed { index, argument ->
                if (argument is Customizer<*>) {
                    @Suppress("UNCHECKED_CAST")
                    (argument as Customizer<Any>).customize(configurerFor(call, index))
                }
            }
            when {
                call.method.name == "build" -> mock<DefaultSecurityFilterChain>()
                call.method.returnType == HttpSecurity::class.java -> call.mock
                else -> Answers.RETURNS_DEEP_STUBS.answer(call)
            }
        }

    private fun configurerFor(
        call: InvocationOnMock,
        index: Int,
    ): Any {
        val type = (call.method.genericParameterTypes[index] as? ParameterizedType)?.actualTypeArguments?.first()
        val raw = ((type as? ParameterizedType)?.rawType ?: type) as? Class<*> ?: Any::class.java
        return if (raw == AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry::class.java) {
            registry
        } else {
            Mockito.mock(raw, Answers.RETURNS_DEEP_STUBS)
        }
    }

    private fun chain(): SecurityConfig =
        SecurityConfig(
            authenticationEntryPoint = mock<JwtAuthenticationEntryPoint>(),
            jwtAuthFilter = mock<JwtAuthFilter>(),
            publicAuthRateLimitFilterProvider = mock(),
            securityCorsProperties = SecurityCorsProperties(),
            openApiPublicEnabled = false,
            requireHttps = true,
            csrfCookieSameSite = "None",
        )

    @Test
    fun `lets anybody make the anonymous reads, and asks everything else to log in`() {
        val http = Mockito.mock(HttpSecurity::class.java, dsl)

        chain().authChain(http, mock())

        verify(registry).requestMatchers(HttpMethod.GET, *SecurityConfig.ANONYMOUS_READS)
        verify(registry).anyRequest()
    }
}
