package net.blueshell.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class FormQuestionDTO {
    @NotBlank
    private String prompt;
    @NotBlank
    private String type;
    private List<@NotBlank String> options;
}
