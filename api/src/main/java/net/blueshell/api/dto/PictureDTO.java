package net.blueshell.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "Picture")
public class PictureDTO extends BaseDTO {
    private Long id;
    private String name;
    private String url;
    private long uploaderId;
    private long eventId;
}
