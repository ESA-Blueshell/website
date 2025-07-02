package net.blueshell.api.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

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

