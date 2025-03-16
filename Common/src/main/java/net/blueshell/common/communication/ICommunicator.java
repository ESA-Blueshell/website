package net.blueshell.common.communication;

import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public interface ICommunicator {
    ResponseEntity<String> sendSync(String url, MessageType type, String body, HashMap<String, Object> parameters);
    ResponseEntity<String> sendAsync(String url, MessageType type, String body, HashMap<String, Object> parameters);
}
