package net.blueshell.common.communication.communicators.base;

import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public interface ICommunicator {
    ResponseEntity<String> sendSync(String url, MessageType type, String body, HashMap<String, Object> parameters);
    String sendAsync(String routeTarget, String body);
    String getName();
}
