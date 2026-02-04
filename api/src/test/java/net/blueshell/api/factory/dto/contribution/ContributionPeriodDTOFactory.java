package net.blueshell.api.factory.dto.contribution;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.contribution.ContributionPeriodDTO;
import net.blueshell.api.factory.dto.BaseDtoFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for ContributionPeriodDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class ContributionPeriodDTOFactory extends BaseDtoFactory<ContributionPeriodDTO> {

    @Override
    public Class<ContributionPeriodDTO> targetType() {
        return ContributionPeriodDTO.class;
    }

    @Override
    public ContributionPeriodDTO createBasic() {
        return new ContributionPeriodDTO(
                today(),
                today().plusMonths(6),
                10.0,
                18.0,
                5.0,
                nextId()
        );
    }
}
