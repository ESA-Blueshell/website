package net.blueshell.api.dto.recovery;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;
import net.blueshell.api.validation.user.UniqueUsername;

@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Data
@Schema(name = "MemberActivationRequest")
public class MemberActivationRequest extends BaseDTO {
    @NotBlank
    private String token;

    @NotBlank
    @UniqueUsername
    private String username;

    @JsonProperty
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
            message = "Password must contain at least one lowercase letter, one uppercase letter, one number, and one special character (@$!%*?&)"
    )
    private String password;
}
