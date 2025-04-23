package net.blueshell.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class PictureDTO extends BaseDTO {
    private Long id;
    private String name;
    private String url;
    private long uploaderId;
    private long eventId;
}
