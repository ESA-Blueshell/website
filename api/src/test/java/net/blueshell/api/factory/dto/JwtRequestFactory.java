package net.blueshell.api.factory.dto;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.request.JwtRequest;
import org.springframework.stereotype.Component;

/**
 * Factory for JwtRequest test instances.
 */
@Component
@RequiredArgsConstructor
public class JwtRequestFactory extends BaseDtoFactory<JwtRequest> {

    @Override
    public Class<JwtRequest> targetType() {
        return JwtRequest.class;
    }

    @Override
    public JwtRequest createBasic() {
        return new JwtRequest(unique("user"), "Password123!");
    }
}
