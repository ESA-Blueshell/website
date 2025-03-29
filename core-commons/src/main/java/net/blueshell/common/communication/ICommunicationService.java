package net.blueshell.common.communication;

import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import org.springframework.http.HttpMethod;

public interface ICommunicationService {

    <T> ResponseEntity<T> sendToAPIGateway(String url, HttpMethod method, Class<T> responseType);
    <T> ResponseEntity<T> sendToBlogService(String url, HttpMethod method, Class<T> responseType);
    <T> ResponseEntity<T> sendToEmailParserService(String url, HttpMethod method, Class<T> responseType);
    <T> ResponseEntity<T> sendToEventParserService(String url, HttpMethod method, Class<T> responseType);
    <T> ResponseEntity<T> sendToSocialMediaService(String url, HttpMethod method, Class<T> responseType);
    <T> ResponseEntity<T> sendToTelemetryService(String url, HttpMethod method, Class<T> responseType);

    <T, T1> ResponseEntity<T> sendToAPIGateway(String url, HttpMethod method, T1 body, Class<T> responseType);
    <T, T1> ResponseEntity<T> sendToBlogService(String url, HttpMethod method, T1 body, Class<T> responseType);
    <T, T1> ResponseEntity<T> sendToEmailParserService(String url, HttpMethod method, T1 body, Class<T> responseType);
    <T, T1> ResponseEntity<T> sendToEventParserService(String url, HttpMethod method, T1 body, Class<T> responseType);
    <T, T1> ResponseEntity<T> sendToSocialMediaService(String url, HttpMethod method, T1 body, Class<T> responseType);
    <T, T1> ResponseEntity<T> sendToTelemetryService(String url, HttpMethod method, T1 body, Class<T> responseType);

    <T, T1> ResponseEntity<T> sendToAPIGateway(String url, HttpMethod method, T1 body,
                                               HashMap<String, Object> parameters, Class<T> responseType);
    <T, T1> ResponseEntity<T> sendToBlogService(String url, HttpMethod method, T1 body,
                                                HashMap<String, Object> parameters, Class<T> responseType);
    <T, T1> ResponseEntity<T> sendToEmailParserService(String url, HttpMethod method, T1 body,
                                                       HashMap<String, Object> parameters, Class<T> responseType);
    <T, T1> ResponseEntity<T> sendToEventParserService(String url, HttpMethod method, T1 body,
                                                       HashMap<String, Object> parameters, Class<T> responseType);
    <T, T1> ResponseEntity<T> sendToSocialMediaService(String url, HttpMethod method, T1 body,
                                                       HashMap<String, Object> parameters, Class<T> responseType);
    <T, T1> ResponseEntity<T> sendToTelemetryService(String url, HttpMethod method, T1 body,
                                                     HashMap<String, Object> parameters, Class<T> responseType);
}
