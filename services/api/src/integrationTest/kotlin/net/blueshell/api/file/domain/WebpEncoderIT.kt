package net.blueshell.api.file.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer

/**
 * What a missing converter does to a deployment.
 *
 * The probe runs from `@PostConstruct`, so the thing worth asserting is not that a method
 * throws but that a context holding this bean refuses to come up: a packaging mistake has to
 * stop a deploy rather than wait for somebody's first upload. That needs a context refreshed
 * for real, which is why this sits in the integration layer rather than beside the pure rules.
 */
class WebpEncoderIT {
    @Test
    fun `a context refuses to start when the converter is absent`() {
        ApplicationContextRunner()
            .withPropertyValues("app.files.cwebp-path=$MISSING_BINARY")
            .withUserConfiguration(MissingConverter::class.java)
            .run { context ->
                assertThat(context).hasFailed()
                // Loudly: the failure says in the site's own words which binary was not there,
                // rather than surfacing a bare exec error somebody has to go and interpret.
                assertThat(context.startupFailure)
                    .hasStackTraceContaining(WebpUnavailableException::class.java.name)
                    .hasStackTraceContaining("Could not probe cwebp")
                    .hasStackTraceContaining(MISSING_BINARY)
            }
    }

    /**
     * The probe covers every binary an animation reaches, not only the one a still does.
     *
     * A runtime image that shipped `cwebp` and nothing else would start, serve every still, and
     * fail on the first animated banner somebody posted, which is a deploy that looks fine.
     * Called directly rather than through a context: the context's part is proven above, and
     * what these add is which binaries the probe covers.
     */
    @Test
    fun `the probe covers the decoder an animation is taken apart with`() {
        assertThatThrownBy { WebpEncoder("cwebp", MISSING_BINARY, "webpmux").verifyAvailable() }
            .isInstanceOf(WebpUnavailableException::class.java)
            .hasMessageContaining("Could not probe dwebp")
            .hasMessageContaining(MISSING_BINARY)
    }

    /**
     * A binary that is there and answers badly is as much a packaging fault as a missing one.
     *
     * A different arm of the same check: one reports that the program could not be run, the
     * other that it ran and said no. Both have to stop a deploy rather than wait for an upload.
     */
    @Test
    fun `a converter that answers with a failure stops the deploy too`() {
        assertThatThrownBy { WebpEncoder(ALWAYS_FAILS, "dwebp", "webpmux").verifyAvailable() }
            .isInstanceOf(WebpUnavailableException::class.java)
            .hasMessageContaining("Could not probe cwebp")
            .hasMessageContaining("exit 1")
    }

    @Test
    fun `the probe covers the muxer an animation is put back together with`() {
        assertThatThrownBy { WebpEncoder("cwebp", "dwebp", MISSING_BINARY).verifyAvailable() }
            .isInstanceOf(WebpUnavailableException::class.java)
            .hasMessageContaining("Could not probe webpmux")
    }

    /**
     * The encoder alone, pointed at a converter that is not installed.
     *
     * The placeholder configurer is what lets the property above reach the bean's `@Value`,
     * which a bare runner has nothing to resolve.
     */
    @Configuration(proxyBeanMethods = false)
    class MissingConverter {
        @Bean
        fun placeholders() = PropertySourcesPlaceholderConfigurer()

        @Bean
        fun webpEncoder() = WebpEncoder(MISSING_BINARY)
    }

    private companion object {
        const val MISSING_BINARY = "__missing-cwebp__"

        /** A program every platform has, which exits non-zero whatever it is asked. */
        const val ALWAYS_FAILS = "false"
    }
}
