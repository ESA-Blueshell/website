package net.blueshell.common.communication.communicators;

import net.blueshell.common.communication.communicators.base.CommunicatorBase;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public class ApiGatewayCommunicator extends CommunicatorBase {

    public static final String name = "apigateway";
    private final String apiGatewayUrl = formatUrl(name, 80);

    @Override
    public <T, T1> ResponseEntity<T> sendSync(String url, HttpMethod method,
                                          T1 body, HashMap<String, Object> parameters,
                                          Class<T> responseType) {
        return super.sendSync(apiGatewayUrl + url, method, body, parameters, responseType);
    }
}
