package net.blueshell.api.dto;


import lombok.*;

import java.sql.Timestamp;

@Data
@EqualsAndHashCode(callSuper = true)
public class BlogDTO extends BaseDTO {
    private String id;
    private String title;
    private String html;
    private Timestamp publishedAt;
}

