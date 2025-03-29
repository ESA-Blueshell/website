package net.blueshell.common.communication;

import net.blueshell.common.communication.communicators.ApiGatewayCommunicator;
import net.blueshell.common.communication.communicators.BlogCommunicator;
import net.blueshell.common.communication.communicators.EmailParserCommunicator;
import net.blueshell.common.communication.communicators.EventParserCommunicator;
import net.blueshell.common.communication.communicators.SocialMediaCommunicator;
import net.blueshell.common.communication.communicators.TelemetryCommunicator;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public class CommunicationService implements ICommunicationService {
    @Override
    public <T> ResponseEntity<T> sendToAPIGateway(String url, HttpMethod method,
                                                  Class<T> responseType) {
        return sendToAPIGateway(url, method, null, null, responseType);
    }

    @Override
    public <T> ResponseEntity<T> sendToBlogService(String url, HttpMethod method,
                                                   Class<T> responseType) {
        return sendToBlogService(url, method, null, null, responseType);
    }

    @Override
    public <T> ResponseEntity<T> sendToEmailParserService(String url, HttpMethod method,
                                                          Class<T> responseType) {
        return sendToEmailParserService(url, method, null, null, responseType);
    }

    @Override
    public <T> ResponseEntity<T> sendToEventParserService(String url, HttpMethod method,
                                                          Class<T> responseType) {
        return sendToEventParserService(url, method, null, null, responseType);
    }

    @Override
    public <T> ResponseEntity<T> sendToSocialMediaService(String url, HttpMethod method,
                                                          Class<T> responseType) {
        return sendToSocialMediaService(url, method, null, null, responseType);
    }

    @Override
    public <T> ResponseEntity<T> sendToTelemetryService(String url, HttpMethod method,
                                                        Class<T> responseType) {
        return sendToTelemetryService(url, method, null, null, responseType);
    }

    @Override
    public <T, T1> ResponseEntity<T> sendToAPIGateway(String url, HttpMethod method, T1 body,
                                                  Class<T> responseType) {
        return sendToAPIGateway(url, method, body, null, responseType);
    }

    @Override
    public <T, T1> ResponseEntity<T> sendToBlogService(String url, HttpMethod method, T1 body,
                                                   Class<T> responseType) {
        return sendToBlogService(url, method, body, null, responseType);
    }

    @Override
    public <T, T1> ResponseEntity<T> sendToEmailParserService(String url, HttpMethod method, T1 body,
                                                          Class<T> responseType) {
        return sendToEmailParserService(url, method, body, null, responseType);
    }

    @Override
    public <T, T1> ResponseEntity<T> sendToEventParserService(String url, HttpMethod method, T1 body,
                                                          Class<T> responseType) {
        return sendToEventParserService(url, method, body, null, responseType);
    }

    @Override
    public <T, T1> ResponseEntity<T> sendToSocialMediaService(String url, HttpMethod method, T1 body,
                                                          Class<T> responseType) {
        return sendToSocialMediaService(url, method, body, null, responseType);
    }

    @Override
    public <T, T1> ResponseEntity<T> sendToTelemetryService(String url, HttpMethod method, T1 body,
                                                        Class<T> responseType) {
        return sendToTelemetryService(url, method, body, null, responseType);
    }

    @Override
    public <T, T1> ResponseEntity<T> sendToAPIGateway(String url, HttpMethod method,
                                                  T1 body, HashMap<String, Object> parameters,
                                                  Class<T> responseType) {
        return new ApiGatewayCommunicator().sendSync(url, method, body, parameters, responseType);
    }

    @Override
    public <T, T1> ResponseEntity<T> sendToBlogService(String url, HttpMethod method,
                                                   T1 body, HashMap<String, Object> parameters,
                                                   Class<T> responseType) {
        return new BlogCommunicator().sendSync(url, method, body, parameters, responseType);
    }

    @Override
    public <T, T1> ResponseEntity<T> sendToEmailParserService(String url, HttpMethod method,
                                                          T1 body, HashMap<String, Object> parameters,
                                                          Class<T> responseType) {
        return new EmailParserCommunicator().sendSync(url, method, body, parameters, responseType);
    }

    @Override
    public <T, T1> ResponseEntity<T> sendToEventParserService(String url, HttpMethod method,
                                                          T1 body, HashMap<String, Object> parameters,
                                                          Class<T> responseType) {
        return new EventParserCommunicator().sendSync(url, method, body, parameters, responseType);
    }

    @Override
    public <T, T1> ResponseEntity<T> sendToSocialMediaService(String url, HttpMethod method,
                                                          T1 body, HashMap<String, Object> parameters,
                                                          Class<T> responseType) {
        return new SocialMediaCommunicator().sendSync(url, method, body, parameters, responseType);
    }

    @Override
    public <T, T1> ResponseEntity<T> sendToTelemetryService(String url, HttpMethod method,
                                                        T1 body, HashMap<String, Object> parameters,
                                                        Class<T> responseType) {
        return new TelemetryCommunicator().sendSync(url, method, body, parameters, responseType);
    }
}
