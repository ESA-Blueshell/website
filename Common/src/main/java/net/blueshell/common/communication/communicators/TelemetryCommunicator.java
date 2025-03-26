package net.blueshell.common.communication.communicators;

import net.blueshell.common.communication.communicators.base.MessageType;
import net.blueshell.common.communication.communicators.base.CommunicatorBase;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public class TelemetryCommunicator extends CommunicatorBase {

    private final String telemetryUrl = "http://telemetry:8080";

    public TelemetryCommunicator() {
        super();
    }

    public TelemetryCommunicator(RabbitTemplate template) {
        super(template);
    }

    @Override
    public ResponseEntity<String> sendSync(String url, MessageType type,
                                           String body, HashMap<String, Object> parameters) {
        return super.sendSync(telemetryUrl + url, type, body, parameters);
    }

    @Override
    public String getName() {
        return "telemetry";
    }
}
