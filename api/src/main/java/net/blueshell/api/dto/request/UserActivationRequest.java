package net.blueshell.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;
import net.blueshell.api.validation.request.ValidUserActivationRequest;
import net.blueshell.api.validation.user.ExistingUsername;

@EqualsAndHashCode(callSuper = true)
@Data
@ValidUserActivationRequest
@Schema(name = "UserActivationRequest")
public class UserActivationRequest extends BaseDTO {

    @NotBlank
    private String token;

    @NotBlank
    @ExistingUsername
    private String username;
}
