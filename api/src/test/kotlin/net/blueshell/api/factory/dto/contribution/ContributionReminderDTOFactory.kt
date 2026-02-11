package net.blueshell.api.factory.dto.contribution

import net.blueshell.api.domain.contribution.web.dto.ContributionReminderDTO
import net.blueshell.api.factory.dto.BaseDtoFactory
import org.springframework.stereotype.Component
import java.sql.Timestamp

/**
 * Factory for ContributionReminderDTO test instances.
 */
@Component
class ContributionReminderDTOFactory : BaseDtoFactory<net.blueshell.api.domain.contribution.web.dto.ContributionReminderDTO>() {

    override fun targetType(): Class<net.blueshell.api.domain.contribution.web.dto.ContributionReminderDTO> = _root_ide_package_.net.blueshell.api.domain.contribution.web.dto.ContributionReminderDTO::class.java

    override fun createBasic(): net.blueshell.api.domain.contribution.web.dto.ContributionReminderDTO {
        return _root_ide_package_.net.blueshell.api.domain.contribution.web.dto.ContributionReminderDTO(
            nextId(),
            nextId(),
            Timestamp.from(now())
        )
    }
}
