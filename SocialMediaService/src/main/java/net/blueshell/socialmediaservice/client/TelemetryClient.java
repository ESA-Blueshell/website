package net.blueshell.socialmediaservice.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class TelemetryClient {
    @Autowired
    private RestTemplate restTemplate;

    private static final String TELEMETRY_SERVICE_URL_TEMPLATE = "http://telemetry-service/api/track-url?type=%s&id=%s";

    public String getTrackableURL(String type, String blogID) {
        String url = String.format(TELEMETRY_SERVICE_URL_TEMPLATE, type, blogID);
        return restTemplate.getForObject(url, String.class);
    }

    public String getTrackableBlogURL(String blogID) {
        return getTrackableURL("blog", blogID);
    }

    public String getTrackableEventURL(String eventID) {
        return getTrackableURL("event", eventID);
    }
}
