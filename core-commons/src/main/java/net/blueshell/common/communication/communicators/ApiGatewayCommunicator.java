package net.blueshell.common.communication.communicators;

import net.blueshell.common.communication.communicators.base.MessageType;
import net.blueshell.common.communication.communicators.base.CommunicatorBase;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public class ApiGatewayCommunicator extends CommunicatorBase {

    public static final String name = "apigateway";
    private final String apiGatewayUrl = formatUrl(name, 80);

    public ApiGatewayCommunicator() {
        super();
    }

    public ApiGatewayCommunicator(RabbitTemplate template) {
        super(template);
    }

    @Override
    public ResponseEntity<String> sendSync(String url, MessageType type,
                                           String body, HashMap<String, Object> parameters) {
        return super.sendSync(apiGatewayUrl + url, type, body, parameters);
    }
}
