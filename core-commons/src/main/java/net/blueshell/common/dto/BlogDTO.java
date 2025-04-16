package net.blueshell.common.dto;


import lombok.*;
import net.blueshell.common.Image;

import java.sql.Timestamp;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class BlogDTO extends BaseDTO {
    private String id;
    private String title;
    private String html;
    private Timestamp publishedAt;
}

