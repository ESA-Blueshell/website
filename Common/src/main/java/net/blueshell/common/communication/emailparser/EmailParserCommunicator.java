package net.blueshell.common.communication.emailparser;

import net.blueshell.common.communication.CommunicatorBase;
import net.blueshell.common.communication.MessageType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public class EmailParserCommunicator extends CommunicatorBase {

    private final String emailParserUrl = "http://emailparser:8080";

    @Override
    public ResponseEntity<String> sendSync(String url, MessageType type,
                                           String body, HashMap<String, Object> parameters) {
        return super.sendSync(emailParserUrl + url, type, body, parameters);
    }

    @Override
    public ResponseEntity<String> sendAsync(String url, MessageType type,
                                            String body, HashMap<String, Object> parameters) {
        return super.sendAsync(emailParserUrl + url, type, body, parameters);
    }
}
