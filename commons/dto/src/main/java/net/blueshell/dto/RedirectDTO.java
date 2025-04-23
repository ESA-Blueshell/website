package net.blueshell.dto;


import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.enums.PlatformType;

import java.sql.Timestamp;

@Data
@EqualsAndHashCode(callSuper = true)
public class RedirectDTO extends BaseDTO {
    private String id;
    private Timestamp created;
    private TelemetryDTO telemetry;
}

