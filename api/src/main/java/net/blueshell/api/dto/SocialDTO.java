package net.blueshell.api.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;
import net.blueshell.api.common.enums.PlatformType;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "Social")
public class SocialDTO extends BaseDTO {
    private String id;
    private String title;
    private String text;
    private String url;
    private PlatformType[] platforms;
}

