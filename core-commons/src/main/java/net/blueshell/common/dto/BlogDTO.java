package net.blueshell.common.dto;


import com.fasterxml.jackson.annotation.JsonSubTypes;
import lombok.*;
import net.blueshell.common.Image;

import java.sql.Timestamp;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class BlogDTO extends BaseDTO {
    private String id;
    private String title;
    private String text;
    private String html;
    private String markdown;
    private List<Image> images;
    private Timestamp publishedAt;
}

