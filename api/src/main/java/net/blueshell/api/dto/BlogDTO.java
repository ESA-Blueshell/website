package net.blueshell.api.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class BlogDTO extends BaseDTO {
    private UUID id;
    private String url;
    private String title;
    private String text;
    private String html;
    private String markdown;
    private Timestamp publishedAt;
}

