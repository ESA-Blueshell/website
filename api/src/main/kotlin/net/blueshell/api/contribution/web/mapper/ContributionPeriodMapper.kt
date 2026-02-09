package net.blueshell.api.contribution.web.mapper

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.contribution.web.dto.ContributionPeriodDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.contribution.persistence.ContributionPeriod
import org.springframework.stereotype.Component

@Konverter
interface ContributionPeriodKonverter {
    fun toDTO(period: ContributionPeriod): ContributionPeriodDTO

    fun fromDTO(dto: ContributionPeriodDTO): ContributionPeriod
}

@Component
class ContributionPeriodMapper : BaseMapper<ContributionPeriod, ContributionPeriodDTO>() {
    private val konverter = konverter<ContributionPeriodKonverter>()

    override fun fromDTO(dto: ContributionPeriodDTO): ContributionPeriod = konverter.fromDTO(dto)

    fun fromDTO(dto: ContributionPeriodDTO, contributionPeriod: ContributionPeriod): ContributionPeriod {
        val mapped = konverter.fromDTO(dto)
        contributionPeriod.startDate = mapped.startDate
        contributionPeriod.endDate = mapped.endDate
        contributionPeriod.halfYearFee = mapped.halfYearFee
        contributionPeriod.fullYearFee = mapped.fullYearFee
        contributionPeriod.alumniFee = mapped.alumniFee
        dto.version?.let { contributionPeriod.version = it }
        return contributionPeriod
    }

    override fun toDTO(contributionPeriod: ContributionPeriod): ContributionPeriodDTO = konverter.toDTO(contributionPeriod)
}

fun ContributionPeriod.asDTO(mapper: ContributionPeriodMapper): ContributionPeriodDTO = mapper.toDTO(this)

fun ContributionPeriodDTO.asEntity(mapper: ContributionPeriodMapper): ContributionPeriod = mapper.fromDTO(this)
