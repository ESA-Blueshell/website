package net.blueshell.api.factory.dto.request;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.recovery.PasswordResetRequest;
import net.blueshell.api.factory.dto.BaseDtoFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for PasswordResetRequest test instances.
 */
@Component
@RequiredArgsConstructor
public class PasswordResetRequestFactory extends BaseDtoFactory<PasswordResetRequest> {

    @Override
    public Class<PasswordResetRequest> targetType() {
        return PasswordResetRequest.class;
    }

    @Override
    public PasswordResetRequest createBasic() {
        PasswordResetRequest dto = new PasswordResetRequest();
        dto.setToken(unique("tok"));
        dto.setPassword("Password123!");
        return dto;
    }
}
