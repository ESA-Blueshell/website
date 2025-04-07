package net.blueshell.common.dto;


import lombok.*;
import net.blueshell.common.Image;

import java.sql.Timestamp;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class SocialDTO extends BaseDTO {
    private String id;
    private String title;
    private String text;
    private String html;
    private List<Image> images;
    private Timestamp publishedAt;
}

