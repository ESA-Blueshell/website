package net.blueshell.api.testsupport

import org.mockito.Answers
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.mock
import org.mockito.stubbing.Answer
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
import org.springframework.security.web.DefaultSecurityFilterChain
import java.lang.reflect.ParameterizedType
import java.lang.reflect.TypeVariable

/**
 * A stand-in [HttpSecurity] that runs every Customizer it is given against a stand-in configurer
 * and keeps the DSL chaining on itself, so a chain's rules can be read without a Spring context.
 */
object StandInHttpSecurity {
    fun create(
        registry: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry =
            mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS),
    ): HttpSecurity =
        Mockito.mock(
            HttpSecurity::class.java,
            Answer { call: InvocationOnMock ->
                call.arguments.forEachIndexed { index, argument ->
                    if (argument is Customizer<*>) {
                        @Suppress("UNCHECKED_CAST")
                        (argument as Customizer<Any>).customize(configurerFor(call, index, registry))
                    }
                }
                when {
                    call.method.name == "build" -> mock<DefaultSecurityFilterChain>()
                    call.method.returnType != Any::class.java && call.method.returnType.isInstance(call.mock) -> call.mock
                    else -> Answers.RETURNS_DEEP_STUBS.answer(call)
                }
            },
        )

    private fun configurerFor(
        call: InvocationOnMock,
        index: Int,
        registry: Any,
    ): Any {
        val type = (call.method.genericParameterTypes[index] as? ParameterizedType)?.actualTypeArguments?.first()
        // `with(configurer, customizer)` hands the customizer the configurer it was given.
        if (type is TypeVariable<*>) {
            val same = call.method.genericParameterTypes.indexOfFirst { it == type }
            if (same >= 0) return call.arguments[same]
        }
        val raw = ((type as? ParameterizedType)?.rawType ?: type) as? Class<*> ?: Any::class.java
        return if (raw == AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry::class.java) {
            registry
        } else {
            Mockito.mock(raw, Answers.RETURNS_DEEP_STUBS)
        }
    }
}
