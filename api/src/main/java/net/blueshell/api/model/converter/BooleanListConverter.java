package net.blueshell.api.model.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Converter
public class BooleanListConverter implements AttributeConverter<List<Boolean>, String> {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<Boolean> attribute) {
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new RuntimeException("JSON conversion error", e);
        }
    }

    @Override
    public List<Boolean> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank() || "null".equalsIgnoreCase(dbData)) {
            return new ArrayList<>();
        }
        try {
            var type = MAPPER.getTypeFactory()
                    .constructCollectionType(List.class, Boolean.class);
            return MAPPER.readValue(dbData, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize JSON to List<FormQuestion>", e);
        }
    }
}