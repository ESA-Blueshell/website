package net.blueshell.api.dto.response;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.BaseDTO;

import java.io.Serial;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
public class JwtResponse extends BaseDTO {

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

    public JwtResponse(String token, long userId, String username, long expiration, Set<Role> roles) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.expiration = expiration;
        this.roles = roles;
    }
}
