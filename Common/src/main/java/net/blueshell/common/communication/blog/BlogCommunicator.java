package net.blueshell.common.communication.blog;

import net.blueshell.common.communication.CommunicatorBase;
import net.blueshell.common.communication.MessageType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public class BlogCommunicator extends CommunicatorBase {

    private final String blogUrl = "http://blogservice:8080";

    @Override
    public ResponseEntity<String> sendSync(String url, MessageType type,
                                           String body, HashMap<String, Object> parameters) {
        return super.sendSync(blogUrl + url, type, body, parameters);
    }

    @Override
    public ResponseEntity<String> sendAsync(String url, MessageType type,
                                            String body, HashMap<String, Object> parameters) {
        return super.sendAsync(blogUrl + url, type, body, parameters);
    }
}
