package net.blueshell.api.factory.dto.contribution

import net.blueshell.api.domain.contribution.web.dto.ContributionPeriodDTO
import net.blueshell.api.factory.dto.BaseDtoFactory
import org.springframework.stereotype.Component

/**
 * Factory for ContributionPeriodDTO test instances.
 */
@Component
class ContributionPeriodDTOFactory : BaseDtoFactory<net.blueshell.api.domain.contribution.web.dto.ContributionPeriodDTO>() {

    override fun targetType(): Class<net.blueshell.api.domain.contribution.web.dto.ContributionPeriodDTO> = _root_ide_package_.net.blueshell.api.domain.contribution.web.dto.ContributionPeriodDTO::class.java

    override fun createBasic(): net.blueshell.api.domain.contribution.web.dto.ContributionPeriodDTO {
        return _root_ide_package_.net.blueshell.api.domain.contribution.web.dto.ContributionPeriodDTO(
            today(),
            today().plusMonths(6),
            10.0,
            18.0,
            5.0,
            nextId()
        )
    }
}
