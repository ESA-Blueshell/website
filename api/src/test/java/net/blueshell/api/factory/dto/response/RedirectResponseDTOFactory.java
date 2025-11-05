package net.blueshell.api.factory.dto.response;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.response.RedirectResponseDTO;
import net.blueshell.api.factory.dto.BaseDtoFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for RedirectResponseDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class RedirectResponseDTOFactory extends BaseDtoFactory<RedirectResponseDTO> {

    @Override
    public Class<RedirectResponseDTO> targetType() {
        return RedirectResponseDTO.class;
    }

    @Override
    public RedirectResponseDTO createBasic() {
        return new RedirectResponseDTO("/test/" + nextId());
    }
}
