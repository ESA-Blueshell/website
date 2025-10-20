package net.blueshell.api.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.validation.user.ValidMobilePhoneNumber;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "AdvancedUser")
public class AdvancedUserDTO extends SimpleUserDTO {

    private Set<Role> roles;
    @NotNull
    private Date dateOfBirth;
    @NotBlank
    private String nationality;
    @NotNull
    private boolean photoConsent;
    @NotNull
    private boolean ehbo;
    @NotNull
    private boolean bhv;
    private boolean enabled;
    private Instant createdAt;
    private String gender;
    private String studentNumber;
    private Long addressId;

    @JsonProperty("roles")
    public List<Role> getRolesSorted() {
        if (roles == null || roles.isEmpty()) return new ArrayList<>();

        return roles.stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .toList();
    }
}