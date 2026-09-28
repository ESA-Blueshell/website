package net.blueshell.api.platform.config

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import jakarta.annotation.security.PermitAll
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.http.server.PathContainer
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.util.pattern.PathPatternParser
import java.lang.reflect.Method

/**
 * Who may read without logging in is decided by [SecurityConfig.ANONYMOUS_READS] alone: method
 * security is not set to read `@PermitAll`, so the annotation does nothing at runtime. These keep
 * the two saying the same thing.
 */
class AnonymousReadsTest {
    @Test
    fun `every read a controller opens to anybody is on the anonymous list`() {
        val missing = openReads.filter { path -> anonymous.none { it.matches(PathContainer.parsePath(path)) } }

        assertThat(missing).isEmpty()
    }

    @Test
    fun `every anonymous read is one a controller opens to anybody, or a route no controller serves`() {
        val unopened =
            SecurityConfig.ANONYMOUS_READS.filter { pattern ->
                pattern !in OPENED_ELSEWHERE &&
                    openReads.none { parser.parse(pattern).matches(PathContainer.parsePath(it)) }
            }

        assertThat(unopened).isEmpty()
    }

    @Test
    fun `nothing under management is read anonymously`() {
        assertThat(SecurityConfig.ANONYMOUS_READS.filter { it.startsWith("/management") }).isEmpty()
    }

    private companion object {
        val parser = PathPatternParser()
        val anonymous = SecurityConfig.ANONYMOUS_READS.map(parser::parse)

        /** Anonymous reads that no `@PermitAll` names, and why each is open anyway. */
        val OPENED_ELSEWHERE =
            mapOf(
                "/events/signups/byAccessToken" to "a guest has no login; method security checks the access token sent",
                "/discord/live/socket" to "the websocket the Discord band follows, not a controller",
                "/actuator/health" to "actuator, not a controller",
                "/actuator/health/**" to "actuator, not a controller",
                "/actuator/prometheus" to "actuator, not a controller",
            )

        /** Every GET path a controller method opens to anybody, path variables left as `{name}`. */
        val openReads: List<String> by lazy {
            ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("net.blueshell.api")
                .filter { it.isAnnotatedWith(Controller::class.java) || it.isMetaAnnotatedWith(Controller::class.java) }
                .map { it.reflect() }
                .flatMap { controller ->
                    val prefixes = pathsOf(AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping::class.java))
                    controller.declaredMethods
                        .filter { isGet(it) && opensToAnybody(controller, it) }
                        .flatMap { method ->
                            val paths = pathsOf(AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping::class.java))
                            prefixes.flatMap { prefix -> paths.map { prefix + it } }
                        }
                }.distinct()
                .sorted()
        }

        fun isGet(method: Method): Boolean =
            AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping::class.java)?.method?.contains(RequestMethod.GET) == true

        fun pathsOf(mapping: RequestMapping?): List<String> = mapping?.path?.toList()?.ifEmpty { null } ?: listOf("")

        fun opensToAnybody(
            controller: Class<*>,
            method: Method,
        ): Boolean =
            method.isAnnotationPresent(PermitAll::class.java) ||
                (controller.isAnnotationPresent(PermitAll::class.java) && !method.isAnnotationPresent(PreAuthorize::class.java))
    }
}
