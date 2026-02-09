package net.blueshell.api.contribution.web.mapper

import net.blueshell.api.contribution.web.dto.ContributionDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.contribution.persistence.Contribution
import org.springframework.stereotype.Component

@Component
class ContributionMapper : BaseMapper<Contribution, ContributionDTO>() {
    override fun fromDTO(dto: ContributionDTO): Contribution = fromDTO(dto, Contribution())

    fun fromDTO(dto: ContributionDTO, contribution: Contribution): Contribution {
        contribution.userId = requireNotNull(dto.userId)
        contribution.contributionPeriodId = requireNotNull(dto.contributionPeriodId)
        dto.version?.let { contribution.version = it }
        return contribution
    }

    override fun toDTO(contribution: Contribution): ContributionDTO {
        return ContributionDTO(
            userId = contribution.userId,
            contributionPeriodId = contribution.contributionPeriodId
        ).also { dto ->
            dto.version = contribution.version
        }
    }
}

fun Contribution.asDTO(mapper: ContributionMapper): ContributionDTO = mapper.toDTO(this)

fun ContributionDTO.asEntity(mapper: ContributionMapper): Contribution = mapper.fromDTO(this)
