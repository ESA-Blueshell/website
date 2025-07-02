package net.blueshell.api.dto;


import lombok.Data;
import lombok.EqualsAndHashCode;

import java.sql.Timestamp;

@Data
@EqualsAndHashCode(callSuper = true)
public class RedirectDTO extends BaseDTO {
    private String id;
    private Timestamp createdAt;
    private TelemetryDTO telemetry;
}

