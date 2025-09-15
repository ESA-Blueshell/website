package net.blueshell.api.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.validation.group.Creation;
import net.blueshell.api.validation.group.Update;
import net.blueshell.api.validation.user.ValidMobilePhoneNumber;

import java.sql.Timestamp;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = false)
public class AdvancedUserDTO extends SimpleUserDTO {

    @JsonProperty
    private Set<Role> roles;

    @JsonProperty
    @NotNull
    private Timestamp dateOfBirth;

    @JsonProperty
    @NotBlank
    @ValidMobilePhoneNumber
    private String phoneNumber;

    @JsonProperty
    @NotBlank
    private String nationality;

    @JsonProperty
    @NotNull
    private boolean photoConsent;

    @JsonProperty
    @NotNull
    private boolean ehbo;

    @JsonProperty
    @NotNull
    private boolean bhv;

    @JsonProperty
    private boolean enabled;

    @JsonProperty
    private Timestamp createdAt;

    @JsonProperty
    private String gender;

    @JsonProperty
    private String studentNumber;
}