package net.blueshell.common.communication.communicators;

import net.blueshell.common.communication.communicators.base.MessageType;
import net.blueshell.common.communication.communicators.base.CommunicatorBase;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public class SocialMediaCommunicator extends CommunicatorBase {

    public static final String name = "socialmedia";
    private final String socialMediaServiceUrl = formatUrl(name, 8080);

    public SocialMediaCommunicator() {
        super();
    }

    public SocialMediaCommunicator(RabbitTemplate template) {
        super(template);
    }

    @Override
    public ResponseEntity<String> sendSync(String url, MessageType type,
                                           String body, HashMap<String, Object> parameters) {
        return super.sendSync(socialMediaServiceUrl + url, type, body, parameters);
    }
}
