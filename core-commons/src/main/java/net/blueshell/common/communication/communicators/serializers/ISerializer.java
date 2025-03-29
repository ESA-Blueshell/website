package net.blueshell.common.communication.communicators.serializers;

import com.fasterxml.jackson.core.JsonProcessingException;

public interface ISerializer {
    <T> String serialize(T object) throws JsonProcessingException;
    <T> T deserialize(String jsonString, Class<T> returnType) throws JsonProcessingException;
}
