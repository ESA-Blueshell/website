package net.blueshell.api.platform.config

import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.cfg.CoercionAction
import tools.jackson.databind.cfg.CoercionInputShape
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.type.LogicalType

@Configuration
class JacksonConfig {

    @Bean
    fun objectMapper(): JsonMapper {
        return JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
            .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }

//            .withCoercionConfigFor(LogicalType.Textual) { it.setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull) }
//            .withCoercionConfigFor(LogicalType.Integer) { it.setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull) }
//            .withCoercionConfigFor(LogicalType.Float) { it.setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull) }
//            .withCoercionConfigFor(LogicalType.Boolean) { it.setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull) }
            .build()
    }
}