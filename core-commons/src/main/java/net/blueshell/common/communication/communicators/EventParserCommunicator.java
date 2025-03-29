package net.blueshell.common.communication.communicators;

import net.blueshell.common.communication.communicators.base.MessageType;
import net.blueshell.common.communication.communicators.base.CommunicatorBase;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public class EventParserCommunicator extends CommunicatorBase {

    public static final String name = "eventparser";
    private final String eventParserUrl = formatUrl(name, 8080);

    public EventParserCommunicator() {
        super();
    }

    public EventParserCommunicator(RabbitTemplate template) {
        super(template);
    }

    @Override
    public ResponseEntity<String> sendSync(String url, MessageType type,
                                           String body, HashMap<String, Object> parameters) {
        return super.sendSync(eventParserUrl + url, type, body, parameters);
    }
}
