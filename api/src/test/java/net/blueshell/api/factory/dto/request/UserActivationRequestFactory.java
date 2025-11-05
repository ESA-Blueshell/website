package net.blueshell.api.factory.dto.request;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.recovery.UserActivationRequest;
import net.blueshell.api.factory.dto.BaseDtoFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for UserActivationRequest test instances.
 */
@Component
@RequiredArgsConstructor
public class UserActivationRequestFactory extends BaseDtoFactory<UserActivationRequest> {

    @Override
    public Class<UserActivationRequest> targetType() {
        return UserActivationRequest.class;
    }

    @Override
    public UserActivationRequest createBasic() {
        UserActivationRequest dto = new UserActivationRequest();
        dto.setToken(unique("tok"));
        return dto;
    }
}
