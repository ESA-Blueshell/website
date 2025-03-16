package net.blueshell.common.communication;

import net.blueshell.common.communication.apigateway.ApiGatewayCommunicator;
import net.blueshell.common.communication.blog.BlogCommunicator;
import net.blueshell.common.communication.emailparser.EmailParserCommunicator;
import net.blueshell.common.communication.eventparser.EventParserCommunicator;
import net.blueshell.common.communication.socialmedia.SocialMediaCommunicator;
import net.blueshell.common.communication.telemetry.TelemetryCommunicator;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public class CommunicationService implements ICommunicationService {
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
