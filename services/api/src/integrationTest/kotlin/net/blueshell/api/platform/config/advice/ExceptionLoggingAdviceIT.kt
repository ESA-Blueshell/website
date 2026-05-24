package net.blueshell.api.platform.config.advice

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@Import(ExceptionLoggingAdviceITConfig::class)
class ExceptionLoggingAdviceIT : UserTestSupport() {

    private val advicelogger =
        LoggerFactory.getLogger(ExceptionLoggingAdvice::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun attachAppender() {
        appender.list.clear()
        appender.start()
        advicelogger.addAppender(appender)
    }

    @AfterEach
    fun detachAppender() {
        advicelogger.detachAppender(appender)
        appender.stop()
    }

    @Test
    fun `ResponseStatusException logs at ERROR and surfaces its status`() {
        val user = createUserWithRole(Role.MEMBER)

        mvc.perform(get("/__it/advice/response-status").with(bearer(user)))
            .andExpect(status().isIAmATeapot)

        val errors = appender.list.filter { it.level == Level.ERROR }
        assertThat(errors).hasSize(1)
        val event = errors.single()
        assertThat(event.formattedMessage)
            .contains("GET", "/__it/advice/response-status", "418", "teapot reason")
        assertThat(event.throwableProxy).isNotNull
        assertThat(event.throwableProxy.className).contains("ResponseStatusException")
    }

    @Test
    fun `AccessDeniedException is logged at ERROR and Spring Security still produces 403`() {
        val user = createUserWithRole(Role.MEMBER)

        mvc.perform(get("/__it/advice/access-denied").with(bearer(user)))
            .andExpect(status().isForbidden)

        val errors = appender.list.filter { it.level == Level.ERROR }
        assertThat(errors).hasSize(1)
        val event = errors.single()
        assertThat(event.formattedMessage)
            .contains("GET", "/__it/advice/access-denied", "AuthorizationDeniedException")
        assertThat(event.throwableProxy.className)
            .contains("AuthorizationDeniedException")
    }

    @Test
    fun `unhandled RuntimeException is logged at ERROR`() {
        val user = createUserWithRole(Role.MEMBER)

        runCatching {
            mvc.perform(get("/__it/advice/runtime").with(bearer(user)))
        }

        val errors = appender.list.filter { it.level == Level.ERROR }
        assertThat(errors).hasSize(1)
        val event = errors.single()
        assertThat(event.throwableProxy.className).contains("IllegalStateException")
        assertThat(event.formattedMessage).contains("boom for tests")
    }
}

@TestConfiguration
class ExceptionLoggingAdviceITConfig {
    @Bean
    fun exceptionLoggingAdviceTestController(): ExceptionLoggingAdviceTestController =
        ExceptionLoggingAdviceTestController()
}

@RestController
@RequestMapping("/__it/advice")
class ExceptionLoggingAdviceTestController {
    @GetMapping("/response-status")
    @PreAuthorize("isAuthenticated()")
    fun throwResponseStatus(): Nothing {
        throw ResponseStatusException(HttpStatus.I_AM_A_TEAPOT, "teapot reason")
    }

    @GetMapping("/access-denied")
    @PreAuthorize("hasRole('NEVER_GRANTED')")
    fun accessDenied(): Nothing = error("unreachable")

    @GetMapping("/runtime")
    @PreAuthorize("isAuthenticated()")
    fun throwRuntime(): Nothing {
        throw IllegalStateException("boom for tests")
    }
}
