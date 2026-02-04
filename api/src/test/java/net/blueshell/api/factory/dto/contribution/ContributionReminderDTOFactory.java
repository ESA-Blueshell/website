package net.blueshell.api.factory.dto.contribution;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.contribution.ContributionReminderDTO;
import net.blueshell.api.factory.dto.BaseDtoFactory;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

/**
 * Factory for ContributionReminderDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class ContributionReminderDTOFactory extends BaseDtoFactory<ContributionReminderDTO> {

    @Override
    public Class<ContributionReminderDTO> targetType() {
        return ContributionReminderDTO.class;
    }

    @Override
    public ContributionReminderDTO createBasic() {
        return new ContributionReminderDTO(
                nextId(),
                nextId(),
                Timestamp.from(now())
        );
    }
}
