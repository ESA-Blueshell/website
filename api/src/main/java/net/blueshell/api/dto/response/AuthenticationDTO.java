package net.blueshell.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.api.base.BaseDTO;
import net.blueshell.api.common.enums.Role;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "Login")
public class AuthenticationDTO extends BaseDTO {

    @Serial
    private static final long serialVersionUID = -8091879091924046844L;

    @NotBlank
    private final String token;

    @NotBlank
    private final long userId;

    @NotBlank
    private final String username;

    @NotBlank
    private final long expiration;

    @NotEmpty
    private final Set<Role> roles;

    public AuthenticationDTO(String token, long userId, String username, long expiration, Set<Role> roles) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.expiration = expiration;
        this.roles = roles;
    }

    @JsonProperty("roles")
    public List<Role> getRolesSorted() {
        if (roles == null || roles.isEmpty()) return new ArrayList<>();

        return roles.stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .toList();
    }
}
