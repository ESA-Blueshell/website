package net.blueshell.api.dto;


import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.common.enums.PlatformType;

@Data
@EqualsAndHashCode(callSuper = true)
public class SocialDTO extends BaseDTO {
    private String id;
    private String title;
    private String text;
    private String url;
    private PlatformType[] platforms;
}

