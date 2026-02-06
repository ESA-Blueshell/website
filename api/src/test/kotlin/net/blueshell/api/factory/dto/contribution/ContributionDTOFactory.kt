package net.blueshell.api.factory.dto.contribution

import net.blueshell.api.dto.contribution.ContributionDTO
import net.blueshell.api.factory.dto.BaseDtoFactory
import org.springframework.stereotype.Component
import java.sql.Timestamp

/**
 * Factory for ContributionDTO test instances.
 */
@Component
class ContributionDTOFactory : BaseDtoFactory<ContributionDTO>() {

    override fun targetType(): Class<ContributionDTO> = ContributionDTO::class.java

    override fun createBasic(): ContributionDTO {
        return ContributionDTO(
            nextId(),
            nextId(),
            Timestamp.from(now())
        )
    }
}
