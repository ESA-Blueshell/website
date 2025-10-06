package net.blueshell.api.config;

import io.swagger.v3.core.converter.ModelConverters;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import net.blueshell.api.dto.PersonalInfoDTO;

@Configuration
public class OpenApiSchemasCustomizer {

    @Bean
    public OpenApiCustomizer registerAdditionalSchemas() {
        return openApi -> {
            var read = ModelConverters.getInstance().read(PersonalInfoDTO.class);
            read.forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
        };
    }
}
