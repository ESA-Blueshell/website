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
        ContributionPeriodDTO dto = new ContributionPeriodDTO();
        dto.setStartDate(today());
        dto.setEndDate(today().plusMonths(6));
        dto.setHalfYearFee(10.0);
        dto.setFullYearFee(18.0);
        dto.setAlumniFee(5.0);
        dto.setListId(nextId());
        return dto;
    }
}
