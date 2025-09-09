package net.blueshell.api.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.dto.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = false)
public class SimpleUserDTO extends BaseDTO {

    @JsonProperty
    @NotBlank
    private String initials;

    @JsonProperty
    @NotBlank
    private String firstName;

    @JsonProperty
    private String prefix;

    @JsonProperty
    @NotBlank
    private String lastName;

    @JsonProperty
    private String fullName;

    @JsonProperty
    @NotBlank
    private String username;

    @JsonProperty
    @NotBlank
    private String discord;

    @JsonProperty
    @NotBlank
    @Email
    private String email;

    @JsonProperty
    @NotNull
    private boolean newsletter;

    @NotBlank
    @Size(min = 8, max = 100)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
            message = "Password must contain at least one lowercase letter, one uppercase letter, one number, and one special character (@$!%*?&)")
    private String password;
}
