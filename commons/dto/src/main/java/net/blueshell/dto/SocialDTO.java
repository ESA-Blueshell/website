package net.blueshell.dto;


import lombok.*;
import net.blueshell.enums.PlatformType;

@Data
@EqualsAndHashCode(callSuper = true)
public class SocialDTO extends BaseDTO {
    private String id;
    private String title;
    private String text;
    private String url;
    private PlatformType[] platforms;
}

