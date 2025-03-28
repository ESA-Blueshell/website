package net.blueshell.telemetry.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Shareable {
    private String id;
    private String url;
    private String type; // facebook, x, etc
    private Integer facebookVisits;
    private Integer xVisits;
}
