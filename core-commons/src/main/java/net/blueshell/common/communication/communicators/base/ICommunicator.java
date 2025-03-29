package net.blueshell.common.communication.communicators.base;

import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public interface ICommunicator {
    <T> ResponseEntity<T> sendSync(String url, MessageType type,
                                          T body, HashMap<String, Object> parameters,
                                          Class<T> responseType);
    String sendAsync(String routeTarget, String body);
}
