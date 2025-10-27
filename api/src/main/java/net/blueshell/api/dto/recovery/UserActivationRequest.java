package net.blueshell.api.dto.recovery;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;

@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Data
@Schema(name = "UserActivationRequest")
public class UserActivationRequest extends BaseDTO {
    @NotBlank
    private String token;
}
