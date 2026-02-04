package net.blueshell.api.factory.dto.contribution;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.contribution.ContributionDTO;
import net.blueshell.api.factory.dto.BaseDtoFactory;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

/**
 * Factory for ContributionDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class ContributionDTOFactory extends BaseDtoFactory<ContributionDTO> {

    @Override
    public Class<ContributionDTO> targetType() {
        return ContributionDTO.class;
    }

    @Override
    public ContributionDTO createBasic() {
        return new ContributionDTO(
                nextId(),
                nextId(),
                Timestamp.from(now())
        );
    }
}
