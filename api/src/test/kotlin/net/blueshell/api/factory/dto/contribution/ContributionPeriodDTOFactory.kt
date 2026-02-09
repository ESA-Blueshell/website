package net.blueshell.api.factory.dto.contribution

import net.blueshell.api.contribution.dto.ContributionPeriodDTO
import net.blueshell.api.factory.dto.BaseDtoFactory
import org.springframework.stereotype.Component

/**
 * Factory for ContributionPeriodDTO test instances.
 */
@Component
class ContributionPeriodDTOFactory : BaseDtoFactory<ContributionPeriodDTO>() {

    override fun targetType(): Class<ContributionPeriodDTO> = ContributionPeriodDTO::class.java

    override fun createBasic(): ContributionPeriodDTO {
        return ContributionPeriodDTO(
            today(),
            today().plusMonths(6),
            10.0,
            18.0,
            5.0,
            nextId()
        )
    }
}
