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
    public <T> ResponseEntity<T> sendToAPIGateway(String url, MessageType type,
                                                  Class<T> responseType) {
        return sendToAPIGateway(url, type, null, null, responseType);
    }

    @Override
    public <T> ResponseEntity<T> sendToBlogService(String url, MessageType type,
                                                   Class<T> responseType) {
        return sendToBlogService(url, type, null, null, responseType);
    }

    @Override
    public <T> ResponseEntity<T> sendToEmailParserService(String url, MessageType type,
                                                          Class<T> responseType) {
        return sendToEmailParserService(url, type, null, null, responseType);
    }

    @Override
    public <T> ResponseEntity<T> sendToEventParserService(String url, MessageType type,
                                                          Class<T> responseType) {
        return sendToEventParserService(url, type, null, null, responseType);
    }

    @Override
    public <T> ResponseEntity<T> sendToSocialMediaService(String url, MessageType type,
                                                          Class<T> responseType) {
        return sendToSocialMediaService(url, type, null, null, responseType);
    }

    @Override
    public <T> ResponseEntity<T> sendToTelemetryService(String url, MessageType type,
                                                        Class<T> responseType) {
        return sendToTelemetryService(url, type, null, null, responseType);
    }

    @Override
    public <T> ResponseEntity<T> sendToAPIGateway(String url, MessageType type, T body,
                                                  Class<T> responseType) {
        return sendToAPIGateway(url, type, body, null, responseType);
    }

    @Override
    public <T> ResponseEntity<T> sendToBlogService(String url, MessageType type, T body,
                                                   Class<T> responseType) {
        return sendToBlogService(url, type, body, null, responseType);
    }

    @Override
    public <T> ResponseEntity<T> sendToEmailParserService(String url, MessageType type, T body,
                                                          Class<T> responseType) {
        return sendToEmailParserService(url, type, body, null, responseType);
    }

    @Override
    public <T> ResponseEntity<T> sendToEventParserService(String url, MessageType type, T body,
                                                          Class<T> responseType) {
        return sendToEventParserService(url, type, body, null, responseType);
    }

    @Override
    public <T> ResponseEntity<T> sendToSocialMediaService(String url, MessageType type, T body,
                                                          Class<T> responseType) {
        return sendToSocialMediaService(url, type, body, null, responseType);
    }

    @Override
    public <T> ResponseEntity<T> sendToTelemetryService(String url, MessageType type, T body,
                                                        Class<T> responseType) {
        return sendToTelemetryService(url, type, body, null, responseType);
    }

    @Override
    public <T> ResponseEntity<T> sendToAPIGateway(String url, MessageType type,
                                                  T body, HashMap<String, Object> parameters,
                                                  Class<T> responseType) {
        return new ApiGatewayCommunicator().sendSync(url, type, body, parameters, responseType);
    }

    @Override
    public <T> ResponseEntity<T> sendToBlogService(String url, MessageType type,
                                                   T body, HashMap<String, Object> parameters,
                                                   Class<T> responseType) {
        return new BlogCommunicator().sendSync(url, type, body, parameters, responseType);
    }

    @Override
    public <T> ResponseEntity<T> sendToEmailParserService(String url, MessageType type,
                                                          T body, HashMap<String, Object> parameters,
                                                          Class<T> responseType) {
        return new EmailParserCommunicator().sendSync(url, type, body, parameters, responseType);
    }

    @Override
    public <T> ResponseEntity<T> sendToEventParserService(String url, MessageType type,
                                                          T body, HashMap<String, Object> parameters,
                                                          Class<T> responseType) {
        return new EventParserCommunicator().sendSync(url, type, body, parameters, responseType);
    }

    @Override
    public <T> ResponseEntity<T> sendToSocialMediaService(String url, MessageType type,
                                                          T body, HashMap<String, Object> parameters,
                                                          Class<T> responseType) {
        return new SocialMediaCommunicator().sendSync(url, type, body, parameters, responseType);
    }

    @Override
    public <T> ResponseEntity<T> sendToTelemetryService(String url, MessageType type,
                                                        T body, HashMap<String, Object> parameters,
                                                        Class<T> responseType) {
        return new TelemetryCommunicator().sendSync(url, type, body, parameters, responseType);
    }
}
