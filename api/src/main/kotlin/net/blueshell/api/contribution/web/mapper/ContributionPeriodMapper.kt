package net.blueshell.api.contribution.web.mapper

import net.blueshell.api.contribution.web.dto.ContributionPeriodDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.contribution.persistence.ContributionPeriod
import org.springframework.stereotype.Component

@Component
class ContributionPeriodMapper : BaseMapper<ContributionPeriod, ContributionPeriodDTO>() {
    override fun fromDTO(dto: ContributionPeriodDTO): ContributionPeriod = fromDTO(dto, ContributionPeriod())

    fun fromDTO(dto: ContributionPeriodDTO, contributionPeriod: ContributionPeriod): ContributionPeriod {
        contributionPeriod.startDate = dto.startDate
        contributionPeriod.endDate = dto.endDate
        contributionPeriod.halfYearFee = dto.halfYearFee
        contributionPeriod.fullYearFee = dto.fullYearFee
        contributionPeriod.alumniFee = dto.alumniFee
        dto.version?.let { contributionPeriod.version = it }
        return contributionPeriod
    }

    override fun toDTO(contributionPeriod: ContributionPeriod): ContributionPeriodDTO {
        return ContributionPeriodDTO(
            startDate = contributionPeriod.startDate,
            endDate = contributionPeriod.endDate,
            halfYearFee = contributionPeriod.halfYearFee,
            fullYearFee = contributionPeriod.fullYearFee,
            alumniFee = contributionPeriod.alumniFee,
            listId = contributionPeriod.listId
        ).also { dto ->
            dto.id = contributionPeriod.id
            dto.version = contributionPeriod.version
        }
    }
}

fun ContributionPeriod.asDTO(mapper: ContributionPeriodMapper): ContributionPeriodDTO = mapper.toDTO(this)

fun ContributionPeriodDTO.asEntity(mapper: ContributionPeriodMapper): ContributionPeriod = mapper.fromDTO(this)
