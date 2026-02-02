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
import net.blueshell.api.validation.group.Administration;
import net.blueshell.api.validation.group.Creation;
import net.blueshell.api.validation.group.Update;
import net.blueshell.api.validation.user.UniqueUser;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "SimpleUser")
@UniqueUser(groups = {Update.class, Creation.class, Administration.class})
public class SimpleUserDTO extends PersonalInfoDTO {

    private Long id;

    private String fullName;

    @NotBlank
    private String initials;

    @NotBlank
    private String firstName;

    private String prefix;

    @NotBlank
    private String lastName;

    @NotBlank
    private String username;

    @NotNull
    private boolean newsletter;

    @NotBlank(groups = {Creation.class})
    @Size(min = 8, max = 100, groups = {Creation.class})
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
            message = "Password must contain at least one lowercase letter, one uppercase letter, one number, and one special character (@$!%*?&)",
            groups = {Creation.class}
    )
    private String password;

    private Long addressId;
}