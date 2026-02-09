package net.blueshell.api.platform.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean

/**
 * Validator bean enabling Jakarta Bean Validation in tests.
 */
@Configuration
class ValidatorConfig {
    @Bean
    fun validator(): LocalValidatorFactoryBean = LocalValidatorFactoryBean()
}
