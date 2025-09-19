package net.blueshell.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.sql.Timestamp;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "Email")
public class EmailDTO extends BaseDTO {
    @NotNull
    private Timestamp publishedAt;
    @NotNull
    @NotBlank
    private String html;
}