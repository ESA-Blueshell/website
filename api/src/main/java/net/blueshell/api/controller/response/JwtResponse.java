package net.blueshell.api.controller.response;

import lombok.Getter;
import net.blueshell.api.dto.BaseDTO;

import java.util.Set;

@Getter
public class JwtResponse extends BaseDTO {

    private static final long serialVersionUID = -8091879091924046844L;
    private final String token;
    private final long userId;
    private final String username;
    private final long expiration;
    private final Set<String> roles;

    public JwtResponse(String token, long userId, String username, long expiration, Set<String> roles) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.expiration = expiration;
        this.roles = roles;
    }
}
