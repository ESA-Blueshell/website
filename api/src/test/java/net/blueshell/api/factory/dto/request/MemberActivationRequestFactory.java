package net.blueshell.api.factory.dto.request;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.recovery.MemberActivationRequest;
import net.blueshell.api.factory.dto.BaseDtoFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for MemberActivationRequest test instances.
 */
@Component
@RequiredArgsConstructor
public class MemberActivationRequestFactory extends BaseDtoFactory<MemberActivationRequest> {

    @Override
    public Class<MemberActivationRequest> targetType() {
        return MemberActivationRequest.class;
    }

    @Override
    public MemberActivationRequest createBasic() {
        MemberActivationRequest dto = new MemberActivationRequest();
        dto.setToken(unique("tok"));
        dto.setUsername(unique("user"));
        dto.setPassword("Password123!");
        return dto;
    }
}
