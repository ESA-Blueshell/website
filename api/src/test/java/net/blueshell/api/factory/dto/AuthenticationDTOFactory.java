package net.blueshell.api.factory.dto;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.response.AuthenticationDTO;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Set;

/**
 * Factory for AuthenticationDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class AuthenticationDTOFactory extends BaseDtoFactory<AuthenticationDTO> {

    @Override
    public Class<AuthenticationDTO> targetType() {
        return AuthenticationDTO.class;
    }

    @Override
    public AuthenticationDTO createBasic() {
        String token = Base64.getEncoder().encodeToString(("t-" + nextId()).getBytes());
        long userId = nextId();
        String username = unique("user");
        long exp = System.currentTimeMillis() + 3600_000;
        return new AuthenticationDTO(token, userId, username, exp, Set.of(Role.MEMBER), null);
    }
}
