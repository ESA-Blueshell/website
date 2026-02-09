package net.blueshell.api.contribution.web.mapper

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.contribution.web.dto.ContributionDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.contribution.persistence.Contribution
import org.springframework.stereotype.Component

@Konverter
interface ContributionKonverter {
    fun toDTO(contribution: Contribution): ContributionDTO

    fun fromDTO(dto: ContributionDTO): Contribution
}

@Component
class ContributionMapper : BaseMapper<Contribution, ContributionDTO>() {
    private val konverter = konverter<ContributionKonverter>()

    override fun fromDTO(dto: ContributionDTO): Contribution = konverter.fromDTO(dto)

    fun fromDTO(dto: ContributionDTO, contribution: Contribution): Contribution {
        val mapped = konverter.fromDTO(dto)
        contribution.userId = mapped.userId
        contribution.contributionPeriodId = mapped.contributionPeriodId
        dto.version?.let { contribution.version = it }
        return contribution
    }

    override fun toDTO(contribution: Contribution): ContributionDTO = konverter.toDTO(contribution)
}

fun Contribution.asDTO(mapper: ContributionMapper): ContributionDTO = mapper.toDTO(this)

fun ContributionDTO.asEntity(mapper: ContributionMapper): Contribution = mapper.fromDTO(this)
