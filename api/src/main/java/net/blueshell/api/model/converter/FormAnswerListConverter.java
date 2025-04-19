package net.blueshell.api.model.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Collections;
import java.util.List;

@Converter
public class FormAnswerListConverter implements AttributeConverter<List<Object>, String> {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final TypeReference<List<Object>> TYPE_REF = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<Object> attribute) {
        if (attribute == null) return null;
        try {
            return mapper.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not convert list to JSON", e);
        }
    }

    @Override
    public List<Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return mapper.readValue(dbData, TYPE_REF);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not convert JSON to list", e);
        }
    }
}