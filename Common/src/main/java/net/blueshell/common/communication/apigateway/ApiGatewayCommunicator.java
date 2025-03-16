package net.blueshell.common.communication.apigateway;

import net.blueshell.common.communication.CommunicatorBase;
import net.blueshell.common.communication.MessageType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public class ApiGatewayCommunicator extends CommunicatorBase {
    private final String apiGatewayUrl = "http://apigateway:80";

    @Override
    public ResponseEntity<String> sendSync(String url, MessageType type,
                                           String body, HashMap<String, Object> parameters) {
        return super.sendSync(apiGatewayUrl + url, type, body, parameters);
    }

    @Override
    public ResponseEntity<String> sendAsync(String url, MessageType type,
                                            String body, HashMap<String, Object> parameters) {
        return super.sendAsync(apiGatewayUrl + url, type, body, parameters);
    }
}
