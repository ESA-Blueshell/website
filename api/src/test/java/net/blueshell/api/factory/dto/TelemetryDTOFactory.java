package net.blueshell.api.factory.dto;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.common.enums.PlatformType;
import net.blueshell.api.dto.TelemetryDTO;
import org.springframework.stereotype.Component;

/**
 * Factory for TelemetryDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class TelemetryDTOFactory extends BaseDtoFactory<TelemetryDTO> {

    @Override
    public Class<TelemetryDTO> targetType() {
        return TelemetryDTO.class;
    }

    @Override
    public TelemetryDTO createBasic() {
        TelemetryDTO dto = new TelemetryDTO();
        dto.setPlatform(PlatformType.FACEBOOK);
        dto.setUrl("https://example.com/" + nextId());
        dto.setCreatedAt(now());
        return dto;
    }
}
