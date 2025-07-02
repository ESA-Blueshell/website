package net.blueshell.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.sql.Timestamp;

@Data
@EqualsAndHashCode(callSuper = true)
public class EmailDTO extends BaseDTO {
    @NotNull
    private Timestamp publishedAt;
    @NotNull
    @NotBlank
    private String html;
}