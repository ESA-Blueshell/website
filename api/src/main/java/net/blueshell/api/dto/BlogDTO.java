package net.blueshell.api.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;

import java.sql.Timestamp;
import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "Blog")
public class BlogDTO extends BaseDTO {
    private Long id;
    private String url;
    @NotBlank
    private String title;
    @NotBlank
    private String html;
    private Instant publishedAt;
}

