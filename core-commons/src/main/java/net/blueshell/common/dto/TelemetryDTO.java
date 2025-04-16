package net.blueshell.common.dto;


import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.common.Image;
import net.blueshell.common.enums.PlatformType;

import java.sql.Timestamp;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class TelemetryDTO extends BaseDTO {
    private String id;
    private String url;
    private PlatformType platform;
    private Timestamp createdAt;
}

