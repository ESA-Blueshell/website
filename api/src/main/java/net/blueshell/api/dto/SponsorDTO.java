package net.blueshell.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "Sponsor")
public class SponsorDTO extends BaseDTO {

    private Long id;

    @NotBlank(message = "Sponsor name cannot be blank.")
    private String name;

    @NotBlank(message = "Sponsor description cannot be blank.")
    private String description;
}
