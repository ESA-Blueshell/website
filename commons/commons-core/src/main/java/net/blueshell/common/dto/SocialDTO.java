package net.blueshell.common.dto;


import lombok.*;
import net.blueshell.common.enums.PlatformType;

import java.sql.Timestamp;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class SocialDTO extends BaseDTO {
    private String id;
    private String title;
    private String text;
    private String url;
    private PlatformType[] platforms;
}

