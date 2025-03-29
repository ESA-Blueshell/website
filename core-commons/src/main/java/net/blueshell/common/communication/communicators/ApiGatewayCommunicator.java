package net.blueshell.common.communication.communicators;

import net.blueshell.common.communication.communicators.base.MessageType;
import net.blueshell.common.communication.communicators.base.CommunicatorBase;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public class ApiGatewayCommunicator extends CommunicatorBase {

    public static final String name = "apigateway";
    private final String apiGatewayUrl = formatUrl(name, 80);

    @Override
    public <T> ResponseEntity<T> sendSync(String url, MessageType type,
                                          T body, HashMap<String, Object> parameters,
                                          Class<T> responseType) {
        return super.sendSync(apiGatewayUrl + url, type, body, parameters, responseType);
    }
}
