package net.blueshell.common.dto;


import lombok.*;
import net.blueshell.common.Image;

import java.sql.Timestamp;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class InternalBlogDTO extends BaseDTO {
    private String id;
    private String url;
    private String title;
    private String text;
    private String html;
    private String markdown;
    private List<Image> images;
    private Timestamp publishedAt;
}

