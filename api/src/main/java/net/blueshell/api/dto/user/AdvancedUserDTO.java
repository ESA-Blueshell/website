package net.blueshell.api.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.survey.QuestionDTO;
import net.blueshell.api.validation.user.ValidMobilePhoneNumber;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "AdvancedUser")
public class AdvancedUserDTO extends SimpleUserDTO {

    private Set<Role> roles;

    @JsonProperty("roles")
    public List<Role> getRolesSorted() {
        if (roles == null || roles.isEmpty()) return new ArrayList<>();

        return roles.stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .toList();
    }

    @JsonProperty
    @NotNull
    private Date dateOfBirth;

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