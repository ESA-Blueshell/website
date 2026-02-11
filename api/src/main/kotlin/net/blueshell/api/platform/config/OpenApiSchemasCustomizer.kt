package net.blueshell.api.platform.config

import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema
import net.blueshell.api.domain.committee.web.dto.SimpleCommitteeDTO
import net.blueshell.api.domain.user.web.dto.PersonalInfoDTO
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiSchemasCustomizer {
    @Bean
    fun registerAdditionalSchemas(): OpenApiCustomizer {
        return OpenApiCustomizer { openApi: OpenAPI? ->
            for (clazz in EXPLICIT_CLASSES) {
                val read = ModelConverters.getInstance().readAll(clazz)
                read.forEach { (name: String?, schema: Schema<*>?) ->
                    openApi!!.components.addSchemas(name, schema)
                }
            }
        }
    }

    companion object {
        private val EXPLICIT_CLASSES: MutableSet<Class<*>> = mutableSetOf(
            PersonalInfoDTO::class.java,
            SimpleCommitteeDTO::class.java
        )
    }
}
