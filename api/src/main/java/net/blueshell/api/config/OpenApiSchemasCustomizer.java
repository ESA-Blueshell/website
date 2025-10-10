package net.blueshell.api.config;

import io.swagger.v3.core.converter.ModelConverters;
import net.blueshell.api.dto.committee.SimpleCommitteeDTO;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import net.blueshell.api.dto.PersonalInfoDTO;

import java.util.Set;

@Configuration
public class OpenApiSchemasCustomizer {

    private static final Set<Class<?>> EXPLICIT_CLASSES = Set.of(
            PersonalInfoDTO.class,
            SimpleCommitteeDTO.class
    );


    @Bean
    public OpenApiCustomizer registerAdditionalSchemas() {
        return openApi -> {
            for (var clazz : EXPLICIT_CLASSES) {
                var read = ModelConverters.getInstance().readAll(clazz);
                read.forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
            }
        };
    }
}
