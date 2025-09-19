package net.blueshell.api.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.sql.Timestamp;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "Redirect")
public class RedirectDTO extends BaseDTO {
    private String id;
    private Timestamp createdAt;
    private TelemetryDTO telemetry;
}

