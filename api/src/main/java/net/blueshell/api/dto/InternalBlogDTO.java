package net.blueshell.api.dto;


import lombok.*;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class InternalBlogDTO extends BaseDTO {
    private UUID id;
    private String url;
    private String title;
    private String text;
    private String html;
    private String markdown;
    private Timestamp publishedAt;
}

