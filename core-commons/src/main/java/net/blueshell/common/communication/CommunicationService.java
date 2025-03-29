package net.blueshell.common.communication;

import net.blueshell.common.communication.communicators.ApiGatewayCommunicator;
import net.blueshell.common.communication.communicators.BlogCommunicator;
import net.blueshell.common.communication.communicators.EmailParserCommunicator;
import net.blueshell.common.communication.communicators.EventParserCommunicator;
import net.blueshell.common.communication.communicators.SocialMediaCommunicator;
import net.blueshell.common.communication.communicators.TelemetryCommunicator;
import net.blueshell.common.communication.communicators.base.MessageType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public class CommunicationService implements ICommunicationService {
    @Override
    public ResponseEntity<String> sendToAPIGateway(String url, MessageType type) {
        return sendToAPIGateway(url, type, null, null);
    }

    @Override
    public ResponseEntity<String> sendToBlogService(String url, MessageType type) {
        return sendToBlogService(url, type, null, null);
    }

    @Override
    public ResponseEntity<String> sendToEmailParserService(String url, MessageType type) {
        return sendToEmailParserService(url, type, null, null);
    }

    @Override
    public ResponseEntity<String> sendToEventParserService(String url, MessageType type) {
        return sendToEventParserService(url, type, null, null);
    }

    @Override
    public ResponseEntity<String> sendToSocialMediaService(String url, MessageType type) {
        return sendToSocialMediaService(url, type, null, null);
    }

    @Override
    public ResponseEntity<String> sendToTelemetryService(String url, MessageType type) {
        return sendToTelemetryService(url, type, null, null);
    }

    @Override
    public ResponseEntity<String> sendToAPIGateway(String url, MessageType type, String body) {
        return sendToAPIGateway(url, type, body, null);
    }

    @Override
    public ResponseEntity<String> sendToBlogService(String url, MessageType type, String body) {
        return sendToBlogService(url, type, body, null);
    }

    @Override
    public ResponseEntity<String> sendToEmailParserService(String url, MessageType type, String body) {
        return sendToEmailParserService(url, type, body, null);
    }

    @Override
    public ResponseEntity<String> sendToEventParserService(String url, MessageType type, String body) {
        return sendToEventParserService(url, type, body, null);
    }

    @Override
    public ResponseEntity<String> sendToSocialMediaService(String url, MessageType type, String body) {
        return sendToSocialMediaService(url, type, body, null);
    }

    @Override
    public ResponseEntity<String> sendToTelemetryService(String url, MessageType type, String body) {
        return sendToTelemetryService(url, type, body, null);
    }

    @Override
    public ResponseEntity<String> sendToAPIGateway(String url, MessageType type,
                                                   String body, HashMap<String, Object> parameters) {
       return new ApiGatewayCommunicator().sendSync(url, type, body, parameters);
    }

    @Override
    public ResponseEntity<String> sendToBlogService(String url, MessageType type,
                                  String body, HashMap<String, Object> parameters) {
        return new BlogCommunicator().sendSync(url, type, body, parameters);
    }

    @Override
    public ResponseEntity<String> sendToEmailParserService(String url, MessageType type,
                                         String body, HashMap<String, Object> parameters) {
        return new EmailParserCommunicator().sendSync(url, type, body, parameters);
    }

    @Override
    public ResponseEntity<String> sendToEventParserService(String url, MessageType type,
                                         String body, HashMap<String, Object> parameters) {
        return new EventParserCommunicator().sendSync(url, type, body, parameters);
    }

    @Override
    public ResponseEntity<String> sendToSocialMediaService(String url, MessageType type,
                                         String body, HashMap<String, Object> parameters) {
        return new SocialMediaCommunicator().sendSync(url, type, body, parameters);
    }

    @Override
    public ResponseEntity<String> sendToTelemetryService(String url, MessageType type,
                                       String body, HashMap<String, Object> parameters) {
        return new TelemetryCommunicator().sendSync(url, type, body, parameters);
    }
}
