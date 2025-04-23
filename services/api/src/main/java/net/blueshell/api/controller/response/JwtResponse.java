package net.blueshell.api.controller.response;

import lombok.Getter;
import net.blueshell.api.base.DTO;
import net.blueshell.api.common.enums.Role;

import java.util.Set;

public class JwtResponse extends DTO {

    private static final long serialVersionUID = -8091879091924046844L;
    private final String jwtToken;
    @Getter
    private final long userId;
    @Getter
    private final String username;
    @Getter
    private final long expiration;
    @Getter
    private final Set<Role> roles;

    public JwtResponse(String jwtToken, long userId, String username, long expiration, Set<Role> roles) {
        this.jwtToken = jwtToken;
        this.userId = userId;
        this.username = username;
        this.expiration = expiration;
        this.roles = roles;
    }

    public String getToken() {
        return this.jwtToken;
    }
}
