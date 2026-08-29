package net.blueshell.api.file.domain

import org.assertj.core.api.Assertions.assertThat
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
    }
}
