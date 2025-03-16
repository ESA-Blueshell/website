package net.blueshell.common.communication.telemetry;

import net.blueshell.common.communication.CommunicatorBase;
import net.blueshell.common.communication.MessageType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public class TelemetryCommunicator extends CommunicatorBase {

    private final String telemetryUrl = "http://telemetry:8080";

    @Override
    public ResponseEntity<String> sendSync(String url, MessageType type,
                                           String body, HashMap<String, Object> parameters) {
        return super.sendSync(telemetryUrl + url, type, body, parameters);
    }

    @Override
    public ResponseEntity<String> sendAsync(String url, MessageType type,
                                            String body, HashMap<String, Object> parameters) {
        return super.sendAsync(telemetryUrl + url, type, body, parameters);
    }
}
