package net.blueshell.api.model.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import net.blueshell.api.model.FormQuestion;

import java.util.ArrayList;
import java.util.List;

@Converter
public class FormQuestionListConverter implements AttributeConverter<List<FormQuestion>, String> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<FormQuestion> attribute) {
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new RuntimeException("JSON conversion error", e);
        }
    }

    @Override
    public List<FormQuestion> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(dbData, new TypeReference<List<FormQuestion>>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("JSON conversion error", e);
        }
    }
}