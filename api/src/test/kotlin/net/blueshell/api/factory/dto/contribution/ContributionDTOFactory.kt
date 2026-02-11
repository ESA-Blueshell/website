package net.blueshell.api.factory.dto.contribution

import net.blueshell.api.domain.contribution.web.dto.ContributionDTO
import net.blueshell.api.factory.dto.BaseDtoFactory
import org.springframework.stereotype.Component
import java.sql.Timestamp

/**
 * Factory for ContributionDTO test instances.
 */
@Component
class ContributionDTOFactory : BaseDtoFactory<net.blueshell.api.domain.contribution.web.dto.ContributionDTO>() {

    override fun targetType(): Class<net.blueshell.api.domain.contribution.web.dto.ContributionDTO> = _root_ide_package_.net.blueshell.api.domain.contribution.web.dto.ContributionDTO::class.java

    override fun createBasic(): net.blueshell.api.domain.contribution.web.dto.ContributionDTO {
        return _root_ide_package_.net.blueshell.api.domain.contribution.web.dto.ContributionDTO(
            nextId(),
            nextId(),
            Timestamp.from(now())
        )
    }
}
