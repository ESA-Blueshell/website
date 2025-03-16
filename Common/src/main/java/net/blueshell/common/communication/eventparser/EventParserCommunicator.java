package net.blueshell.common.communication.eventparser;

import net.blueshell.common.communication.CommunicatorBase;
import net.blueshell.common.communication.MessageType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public class EventParserCommunicator extends CommunicatorBase {

    private final String eventParserUrl = "http://emailparser:8080";

    @Override
    public ResponseEntity<String> sendSync(String url, MessageType type,
                                           String body, HashMap<String, Object> parameters) {
        return super.sendSync(eventParserUrl + url, type, body, parameters);
    }

    @Override
    public ResponseEntity<String> sendAsync(String url, MessageType type,
                                            String body, HashMap<String, Object> parameters) {
        return super.sendAsync(eventParserUrl + url, type, body, parameters);
    }
}
