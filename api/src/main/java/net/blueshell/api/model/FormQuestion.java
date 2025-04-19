package net.blueshell.api.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class FormQuestion {
    @NotBlank
    private String prompt;
    @NotBlank
    private String type;
    private List<@NotBlank String> options;
}
