package net.blueshell.dto;


import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.enums.PlatformType;

import java.sql.Timestamp;

@Data
@EqualsAndHashCode(callSuper = true)
public class TelemetryDTO extends BaseDTO {
    private String id;
    private String url;
    private PlatformType platform;
    private Timestamp createdAt;
}

