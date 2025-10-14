package net.blueshell.api.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.dto.PersonalInfoDTO;
import net.blueshell.api.validation.group.Creation;
import net.blueshell.api.validation.group.Update;
import net.blueshell.api.validation.user.UniqueUser;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "SimpleUser")
@UniqueUser(groups = {Update.class, Creation.class})
public class SimpleUserDTO extends PersonalInfoDTO {

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
    @NotBlank
    private String username;

    @JsonProperty
    @NotNull
    private boolean newsletter;

    @NotBlank(groups = {Creation.class})
    @Size(min = 8, max = 100, groups = {Creation.class})
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
            message = "Password must contain at least one lowercase letter, one uppercase letter, one number, and one special character (@$!%*?&)",
            groups = {Creation.class}
    )
    @JsonProperty
    private String password;
}