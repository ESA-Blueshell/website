package net.blueshell.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

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