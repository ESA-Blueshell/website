package net.blueshell.common.communication;

import net.blueshell.common.communication.communicators.base.MessageType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public interface ICommunicationService {

    ResponseEntity<String> sendToAPIGateway(String url, MessageType type);
    ResponseEntity<String> sendToBlogService(String url, MessageType type);
    ResponseEntity<String> sendToEmailParserService(String url, MessageType type);
    ResponseEntity<String> sendToEventParserService(String url, MessageType type);
    ResponseEntity<String> sendToSocialMediaService(String url, MessageType type);
    ResponseEntity<String> sendToTelemetryService(String url, MessageType type);

    ResponseEntity<String> sendToAPIGateway(String url, MessageType type,
                                            String body);
    ResponseEntity<String> sendToBlogService(String url, MessageType type,
                                             String body);
    ResponseEntity<String> sendToEmailParserService(String url, MessageType type,
                                                    String body);
    ResponseEntity<String> sendToEventParserService(String url, MessageType type,
                                                    String body);
    ResponseEntity<String> sendToSocialMediaService(String url, MessageType type,
                                                    String body);
    ResponseEntity<String> sendToTelemetryService(String url, MessageType type,
                                                  String body);

    ResponseEntity<String> sendToAPIGateway(String url, MessageType type,
                                            String body, HashMap<String, Object> parameters);
    ResponseEntity<String> sendToBlogService(String url, MessageType type,
                                             String body, HashMap<String, Object> parameters);
    ResponseEntity<String> sendToEmailParserService(String url, MessageType type,
                                                    String body, HashMap<String, Object> parameters);
    ResponseEntity<String> sendToEventParserService(String url, MessageType type,
                                                    String body, HashMap<String, Object> parameters);
    ResponseEntity<String> sendToSocialMediaService(String url, MessageType type,
                                                    String body, HashMap<String, Object> parameters);
    ResponseEntity<String> sendToTelemetryService(String url, MessageType type,
                                                  String body, HashMap<String, Object> parameters);
}
