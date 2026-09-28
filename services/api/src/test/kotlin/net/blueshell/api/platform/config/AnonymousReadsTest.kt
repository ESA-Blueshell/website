package net.blueshell.api.platform.config

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import jakarta.annotation.security.PermitAll
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import java.lang.reflect.Method

/**
 * The anonymous reads the auth chain is built with: every GET a controller method opens with
 * `@PermitAll`, and a short list of reads opened for another reason.
 */
class AnonymousReadsTest {
    private class Pages {
        @PermitAll
        fun open() = Unit

        @PreAuthorize("isAuthenticated()")
        fun guarded() = Unit

        fun bare() = Unit
    }

    @PermitAll
    private class OpenPages {
        fun open() = Unit

        @PreAuthorize("hasAuthority('BOARD')")
        fun guarded() = Unit
    }

    private fun info(
        path: String,
        method: RequestMethod = RequestMethod.GET,
    ) = RequestMappingInfo.paths(path).methods(method).build()

    private fun handler(
        bean: Any,
        name: String,
    ) = HandlerMethod(bean, bean::class.java.getDeclaredMethod(name))

    @Test
    fun `opens the reads a method or its controller says anybody may make`() {
        val handlers =
            mapOf(
                info("/open/{id}") to handler(Pages(), "open"),
                info("/pages") to handler(OpenPages(), "open"),
                info("/guarded") to handler(Pages(), "guarded"),
                info("/bare") to handler(Pages(), "bare"),
                info("/board") to handler(OpenPages(), "guarded"),
                info("/open", RequestMethod.POST) to handler(Pages(), "open"),
            )

        assertThat(AnonymousReads.permitAllReads(handlers)).containsExactly("/open/{id}", "/pages")
        assertThat(AnonymousReads.of(handlers)).containsAll(AnonymousReads.OPENED_ELSEWHERE.keys).contains("/open/{id}")
    }

    @Test
    fun `each read opened for another reason is one no @PermitAll handler opens`() {
        assertThat(AnonymousReads.OPENED_ELSEWHERE.keys.intersect(openReads.toSet())).isEmpty()
    }

    @Test
    fun `nothing under management is read anonymously`() {
        assertThat((openReads + AnonymousReads.OPENED_ELSEWHERE.keys).filter { it.startsWith("/management") }).isEmpty()
    }

    private companion object {
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
