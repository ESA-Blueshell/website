package net.blueshell.api.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.validation.group.Creation;
import net.blueshell.api.validation.group.Update;
import net.blueshell.api.validation.user.UniqueUser;
import net.blueshell.api.validation.user.ValidMobilePhoneNumber;

import java.sql.Timestamp;
import java.util.Set;

@Data
@UniqueUser
@EqualsAndHashCode(callSuper = false)
public class AdvancedUserDTO extends SimpleUserDTO {

    @NotNull(groups = {Update.class})
    private Long id;

    @JsonProperty
    @NotBlank(groups = {Creation.class})
    private String initials;

    @JsonProperty
    @NotBlank(groups = {Creation.class})
    private String firstName;

    @JsonProperty
    private String prefix;

    @JsonProperty
    @NotBlank(groups = {Creation.class})
    private String lastName;

    @JsonProperty
    private String fullName;

    @JsonProperty
    @NotBlank(groups = {Creation.class})
    private String username;

    @JsonProperty
    private Set<Role> roles;

    @JsonProperty
    @NotBlank(groups = {Creation.class, Update.class})
    private String discord;

    @JsonProperty
    @NotNull(groups = {Creation.class})
    private Timestamp dateOfBirth;

    @JsonProperty
    @NotBlank(groups = {Creation.class})
    @Email(groups = {Creation.class})
    private String email;

    @JsonProperty
    @NotBlank(groups = {Creation.class, Update.class})
    @ValidMobilePhoneNumber(groups = {Creation.class, Update.class})
    private String phoneNumber;

    @JsonProperty
    @NotBlank(groups = {Creation.class})
    private String nationality;

    @JsonProperty
    @NotNull(groups = {Creation.class, Update.class})
    private boolean newsletter;

    @JsonProperty
    @NotNull(groups = {Creation.class, Update.class})
    private boolean photoConsent;

    @JsonProperty
    @NotNull(groups = {Creation.class, Update.class})
    private boolean ehbo;

    @JsonProperty
    @NotNull(groups = {Creation.class, Update.class})
    private boolean bhv;

    @JsonProperty
    private boolean enabled;

    @JsonProperty
    private Timestamp createdAt;

    @JsonProperty
    private String gender;

    @JsonProperty
    private String studentNumber;

    @NotBlank
    @Size(min = 8, max = 100)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
            message = "Password must contain at least one lowercase letter, one uppercase letter, one number, and one special character (@$!%*?&)")
    private String password;
}
