package net.blueshell.api.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "Blog")
public class BlogDTO extends BaseDTO {
    private UUID id;
    private String url;
    @NotBlank
    private String title;
    private String text;
    private String html;
    private String markdown;
    private Timestamp publishedAt;
}

