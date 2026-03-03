package net.blueshell.api.platform.config

import io.swagger.v3.core.util.Json
import io.swagger.v3.core.util.Yaml
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration

/**
 * Configures SpringDoc/swagger-core's internal Jackson 2.x ObjectMapper with KotlinModule so that
 * non-nullable Kotlin types are correctly reflected as `required` fields in the generated OpenAPI spec.
 *
 * SpringDoc uses its own `com.fasterxml.jackson.databind.ObjectMapper` (via `io.swagger.v3.core.util.Json`)
 * entirely separately from the application's `tools.jackson` mapper. Without KotlinModule registered
 * on this internal mapper, Jackson 2.x falls back to Java reflection, which treats all reference types
 * as nullable regardless of Kotlin's type-level non-nullability.
 */
@Configuration
class SpringDocConfig {

    @PostConstruct
    fun registerKotlinModuleWithSwaggerCore() {
        val kotlinModule = com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build()
        Json.mapper().registerModule(kotlinModule)
        Yaml.mapper().registerModule(kotlinModule)
    }
}
