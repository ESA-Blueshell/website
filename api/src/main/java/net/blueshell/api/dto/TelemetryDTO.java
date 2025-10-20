package net.blueshell.api.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;
import net.blueshell.api.common.enums.PlatformType;

import java.sql.Timestamp;
import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "Telemetry")
public class TelemetryDTO extends BaseDTO {
    private String id;
    private String url;
    private PlatformType platform;
    private Instant createdAt;
}

