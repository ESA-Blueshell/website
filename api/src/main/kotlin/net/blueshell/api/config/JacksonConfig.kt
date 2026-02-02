package net.blueshell.api.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.cfg.CoercionAction
import com.fasterxml.jackson.databind.cfg.CoercionInputShape
import com.fasterxml.jackson.databind.type.LogicalType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

@Configuration
class JacksonConfig {
    @Bean
    fun objectMapper(builder: Jackson2ObjectMapperBuilder): ObjectMapper {
        val mapper = builder
            .failOnUnknownProperties(false)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .featuresToEnable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
            .build<ObjectMapper>()
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
