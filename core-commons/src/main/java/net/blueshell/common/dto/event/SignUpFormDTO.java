package net.blueshell.common.dto.event;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class SignUpFormDTO {
    @NotBlank
    private String prompt;
    @NotBlank
    private String type;
    private List<@NotBlank String> options;
}
