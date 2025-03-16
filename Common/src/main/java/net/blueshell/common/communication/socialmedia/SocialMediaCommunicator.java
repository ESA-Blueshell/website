package net.blueshell.common.communication.socialmedia;

import net.blueshell.common.communication.CommunicatorBase;
import net.blueshell.common.communication.MessageType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public class SocialMediaCommunicator extends CommunicatorBase {

    private final String socialMediaServiceUrl = "http://socialmediaservice:8080";

    @Override
    public ResponseEntity<String> sendSync(String url, MessageType type,
                                           String body, HashMap<String, Object> parameters) {
        return super.sendSync(socialMediaServiceUrl + url, type, body, parameters);
    }

    @Override
    public ResponseEntity<String> sendAsync(String url, MessageType type,
                                            String body, HashMap<String, Object> parameters) {
        return super.sendAsync(socialMediaServiceUrl + url, type, body, parameters);
    }
}
