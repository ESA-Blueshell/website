package net.blueshell.api.factory.dto;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.SponsorDTO;
import org.springframework.stereotype.Component;

/**
 * Factory for SponsorDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class SponsorDTOFactory extends BaseDtoFactory<SponsorDTO> {

    @Override
    public Class<SponsorDTO> targetType() {
        return SponsorDTO.class;
    }

    @Override
    public SponsorDTO createBasic() {
        SponsorDTO dto = new SponsorDTO();
        dto.setName(unique("Sponsor"));
        dto.setDescription("Test sponsor description");
        return dto;
    }
}
