package net.blueshell.common.communication.communicators.serializers;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.logging.Level;
import java.util.logging.Logger;

public class JsonSerializer implements ISerializer {

    private static final Logger logger = Logger.getLogger(JsonSerializer.class.getName());
    private final ObjectMapper objectMapper;

    public JsonSerializer() {
        this.objectMapper = new ObjectMapper();
    }

    public <T> String serialize(T object) throws JsonProcessingException {
        return objectMapper.writeValueAsString(object);
    }

    public <T> T deserialize(String jsonString, Class<T> returnType) throws JsonProcessingException {
        try {
            return objectMapper.readValue(jsonString, returnType);
        } catch (JsonParseException ex) {
            logger.log(Level.SEVERE, ex.getMessage());
            if(returnType == String.class) {
                return (T)jsonString;
            } else {
                throw ex;
            }
        }
    }
}
