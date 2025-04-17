package net.blueshell.socialmediaservice.client;

import net.blueshell.common.communicator.TelemetryCommunicator;
import net.blueshell.common.dto.TelemetryDTO;
import net.blueshell.common.enums.PlatformType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component
public class TelemetryClient {

    @Autowired
    private TelemetryCommunicator telemetryCommunicator;

    public String getTrackableURL(PlatformType platform, String url) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("platform", platform);
        params.put("url", url);
        TelemetryDTO telemetryDTO = telemetryCommunicator.sendSync("/telemetry", HttpMethod.POST, params, TelemetryDTO.class);
        return telemetryDTO.getUrl();
    }
}
