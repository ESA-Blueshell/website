package net.blueshell.api.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;

import java.sql.Timestamp;
import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "Redirect")
public class RedirectDTO extends BaseDTO {
    private String id;
    private Instant createdAt;
    private TelemetryDTO telemetry;
}

