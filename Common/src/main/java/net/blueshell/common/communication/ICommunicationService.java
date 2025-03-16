package net.blueshell.common.communication;

import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public interface ICommunicationService {
    ResponseEntity<String> sendToAPIGateway(String url, MessageType type, String body, HashMap<String, Object> parameters);
    ResponseEntity<String> sendToBlogService(String url, MessageType type, String body, HashMap<String, Object> parameters);
    ResponseEntity<String> sendToEmailParserService(String url, MessageType type, String body, HashMap<String, Object> parameters);
    ResponseEntity<String> sendToEventParserService(String url, MessageType type, String body, HashMap<String, Object> parameters);
    ResponseEntity<String> sendToSocialMediaService(String url, MessageType type, String body, HashMap<String, Object> parameters);
    ResponseEntity<String> sendToTelemetryService(String url, MessageType type, String body, HashMap<String, Object> parameters);
}
