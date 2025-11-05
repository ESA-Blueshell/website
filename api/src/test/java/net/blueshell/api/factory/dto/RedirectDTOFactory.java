package net.blueshell.api.factory.dto;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.RedirectDTO;
import org.springframework.stereotype.Component;

/**
 * Factory for RedirectDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class RedirectDTOFactory extends BaseDtoFactory<RedirectDTO> {

    private final TelemetryDTOFactory telemetryFactory;

    @Override
    public Class<RedirectDTO> targetType() {
        return RedirectDTO.class;
    }

    @Override
    public RedirectDTO createBasic() {
        RedirectDTO dto = new RedirectDTO();
        dto.setCreatedAt(now());
        dto.setTelemetry(telemetryFactory.createBasic());
        return dto;
    }
}
