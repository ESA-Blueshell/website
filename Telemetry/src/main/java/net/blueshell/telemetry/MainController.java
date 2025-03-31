package net.blueshell.telemetry;

import net.blueshell.common.Blog;
import net.blueshell.common.Event;
import net.blueshell.telemetry.model.MetricResponse;
import net.blueshell.telemetry.service.TelemetryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class MainController {
    @Autowired
    private TelemetryService telemetryService;

    @GetMapping("/")
    public Boolean checkHealth() {
        return true;
    }

    @GetMapping("/metric")
    public ResponseEntity<MetricResponse> getOverallMetric(
            @RequestParam("timespan") int days,
            @RequestParam("type") String type
    ) {
        MetricResponse res = telemetryService.getTotalMetrics(days, type);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/metric/{id}")
    public ResponseEntity<MetricResponse> getMetricForId(
            @PathVariable("id") String id,
            @RequestParam("timespan") int days
    ) {
        MetricResponse res = telemetryService.getMetricsForId(days, id);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/shareable/{id}")
    public ResponseEntity<String> trackVisit(
            @PathVariable("id") String id,
            @RequestParam("src") String src
    ) {
        String redirectUrl = telemetryService.trackAndExchangeURL(id, src);
        return ResponseEntity.status(302).header("Location", redirectUrl).build();
    }

    @PostMapping("/create-shareable")
    public ResponseEntity<String> createShareable(
            @RequestParam("url") String url,
            @RequestParam("type") String type
    ) {
        String shareableId = telemetryService.createShareable(url, type);
        return ResponseEntity.ok(shareableId);
    }

}

