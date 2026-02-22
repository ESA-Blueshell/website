package net.blueshell.api.platform.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.cfg.CoercionAction
import com.fasterxml.jackson.databind.cfg.CoercionInputShape
import com.fasterxml.jackson.databind.type.LogicalType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonConfig {
    @Bean
    fun objectMapper(): ObjectMapper {
        val mapper = ObjectMapper().findAndRegisterModules()
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
        mapper.coercionConfigFor(LogicalType.Textual)
            .setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull)
        mapper.coercionConfigFor(LogicalType.Integer)
            .setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull)
        mapper.coercionConfigFor(LogicalType.Float)
            .setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull)
        mapper.coercionConfigFor(LogicalType.Boolean)
            .setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull)
        return mapper
    }
}
