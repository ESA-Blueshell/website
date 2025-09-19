package net.blueshell.api.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.dto.BaseDTO;
import net.blueshell.api.validation.group.Administration;
import net.blueshell.api.validation.group.Creation;
import net.blueshell.api.validation.group.Update;
import net.blueshell.api.validation.user.UniqueUser;
import net.blueshell.api.validation.user.ValidMobilePhoneNumber;

@Data
@EqualsAndHashCode(callSuper = false)
@UniqueUser
@Schema(name = "SimpleUser")
public class SimpleUserDTO extends BaseDTO {

    @JsonProperty
    private Long id;

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

    @JsonProperty
    @NotBlank(groups = {Creation.class})
    @Size(min = 8, max = 100, groups = {Creation.class})
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
            message = "Password must contain at least one lowercase letter, one uppercase letter, one number, and one special character (@$!%*?&)",
            groups = {Creation.class}
    )
    private String password;
}