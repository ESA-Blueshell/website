package net.blueshell.telemetry.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricResponse {
    private String type;         // Blog or Event
    private String id;           // optional
    private Integer timespan;     // e.g. "7 days"
    private Integer facebookViews;
    private Integer xViews;
}

