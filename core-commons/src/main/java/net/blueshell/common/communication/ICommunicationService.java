package net.blueshell.common.communication;

import net.blueshell.common.communication.communicators.base.MessageType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public interface ICommunicationService {

    <T> ResponseEntity<T> sendToAPIGateway(String url, MessageType type, Class<T> responseType);
    <T> ResponseEntity<T> sendToBlogService(String url, MessageType type, Class<T> responseType);
    <T> ResponseEntity<T> sendToEmailParserService(String url, MessageType type, Class<T> responseType);
    <T> ResponseEntity<T> sendToEventParserService(String url, MessageType type, Class<T> responseType);
    <T> ResponseEntity<T> sendToSocialMediaService(String url, MessageType type, Class<T> responseType);
    <T> ResponseEntity<T> sendToTelemetryService(String url, MessageType type, Class<T> responseType);

    <T> ResponseEntity<T> sendToAPIGateway(String url, MessageType type, T body, Class<T> responseType);
    <T> ResponseEntity<T> sendToBlogService(String url, MessageType type, T body, Class<T> responseType);
    <T> ResponseEntity<T> sendToEmailParserService(String url, MessageType type, T body, Class<T> responseType);
    <T> ResponseEntity<T> sendToEventParserService(String url, MessageType type, T body, Class<T> responseType);
    <T> ResponseEntity<T> sendToSocialMediaService(String url, MessageType type, T body, Class<T> responseType);
    <T> ResponseEntity<T> sendToTelemetryService(String url, MessageType type, T body, Class<T> responseType);

    <T> ResponseEntity<T> sendToAPIGateway(String url, MessageType type, T body,
                                           HashMap<String, Object> parameters, Class<T> responseType);
    <T> ResponseEntity<T> sendToBlogService(String url, MessageType type, T body,
                                            HashMap<String, Object> parameters, Class<T> responseType);
    <T> ResponseEntity<T> sendToEmailParserService(String url, MessageType type, T body,
                                                   HashMap<String, Object> parameters, Class<T> responseType);
    <T> ResponseEntity<T> sendToEventParserService(String url, MessageType type, T body,
                                                   HashMap<String, Object> parameters, Class<T> responseType);
    <T> ResponseEntity<T> sendToSocialMediaService(String url, MessageType type, T body,
                                                   HashMap<String, Object> parameters, Class<T> responseType);
    <T> ResponseEntity<T> sendToTelemetryService(String url, MessageType type, T body,
                                                 HashMap<String, Object> parameters, Class<T> responseType);
}
