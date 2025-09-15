package net.blueshell.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.dto.BaseDTO;
import net.blueshell.api.validation.user.ExistingUsername;
import net.blueshell.api.validation.request.ValidUserActivationRequest;

@EqualsAndHashCode(callSuper = true)
@Data
@ValidUserActivationRequest
public class UserActivationRequest extends BaseDTO {

    @NotBlank
    private String token;

    @NotBlank
    @ExistingUsername
    private String username;

    @JsonProperty
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
            message = "Password must contain at least one lowercase letter, one uppercase letter, one number, and one special character (@$!%*?&)"
    )
    private String password;
}
