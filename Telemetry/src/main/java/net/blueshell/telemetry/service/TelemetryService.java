package net.blueshell.telemetry.service;

import net.blueshell.telemetry.data.MockDB;
import net.blueshell.telemetry.model.MetricResponse;
import net.blueshell.telemetry.model.Shareable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TelemetryService {

    @Autowired
    MockDB mockDB = new MockDB();

    // TODO: Fetch actual data from DB
    public MetricResponse getTotalMetrics(int days, String type) {
        // Dummy response for now
        return MetricResponse.builder()
                .type(type)
                .timespan(days)
                .facebookViews(314)
                .xViews(127)
                .build();
    }

    // TODO: Fetch actual data from DB
    public MetricResponse getMetricsForId(int days, String id) {
        Shareable shareable = mockDB.findById(id);
        return MetricResponse.builder()
                .xViews(shareable.getXVisits())
                .facebookViews(shareable.getFacebookVisits())
                .timespan(days)
                .type(shareable.getType())
                .build();
    }

    public String trackAndExchangeURL(String id, String src) {
        Shareable shareable = mockDB.findById(id);
        trackVisit(id, src);
        return shareable.getUrl();
    }

    public String createShareable(String url, String type) {
        String id = UUID.randomUUID().toString();

        Shareable shareable = Shareable.builder()
                .id(id)
                .url(url)
                .facebookVisits(0)
                .xVisits(0)
                .build();

        mockDB.save(shareable);
        return id;
    }

    private void trackVisit(String id, String type) {
        //TODO: Track in DB
        mockDB.incrementVisit(id, type);
    }
}
