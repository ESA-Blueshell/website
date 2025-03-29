package net.blueshell.common.communication.communicators;

import net.blueshell.common.communication.communicators.base.MessageType;
import net.blueshell.common.communication.communicators.base.CommunicatorBase;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public class EmailParserCommunicator extends CommunicatorBase {

    public static final String name = "emailparser";
    private final String emailParserUrl = formatUrl(name, 8080);

    public <T> ResponseEntity<T> sendSync(String url, MessageType type,
                                          T body, HashMap<String, Object> parameters,
                                          Class<T> responseType) {
        return super.sendSync(emailParserUrl + url, type, body, parameters, responseType);
    }
}
