package net.blueshell.api.factory.dto.contribution

import net.blueshell.api.contribution.web.dto.ContributionReminderDTO
import net.blueshell.api.factory.dto.BaseDtoFactory
import org.springframework.stereotype.Component
import java.sql.Timestamp

/**
 * Factory for ContributionReminderDTO test instances.
 */
@Component
class ContributionReminderDTOFactory : BaseDtoFactory<ContributionReminderDTO>() {

    override fun targetType(): Class<ContributionReminderDTO> = ContributionReminderDTO::class.java

    override fun createBasic(): ContributionReminderDTO {
        return ContributionReminderDTO(
            nextId(),
            nextId(),
            Timestamp.from(now())
        )
    }
}
