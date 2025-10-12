package net.blueshell.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "Sponsor")
public class SponsorDTO extends BaseDTO {

    private Long id;

    @NotBlank(message = "Sponsor name cannot be blank.")
    @Size(max = 255, message = "Sponsor name cannot exceed 255 characters.")
    private String name;

    @NotBlank(message = "Sponsor description cannot be empty.")
    @Size(max = 4095, message = "Sponsor description cannot exceed 4095 characters.")
    private String description;
}
