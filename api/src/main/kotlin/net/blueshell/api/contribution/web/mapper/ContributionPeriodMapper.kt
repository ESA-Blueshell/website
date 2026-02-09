package net.blueshell.api.contribution.web.mapper

import net.blueshell.api.contribution.web.dto.ContributionPeriodDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.contribution.persistence.ContributionPeriod
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget


@Mapper(componentModel = "spring")
abstract class ContributionPeriodMapper : BaseMapper<ContributionPeriod, ContributionPeriodDTO>() {
    @Mapping(target = "startDate")
    @Mapping(target = "endDate")
    @Mapping(target = "halfYearFee")
    @Mapping(target = "fullYearFee")
    @Mapping(target = "alumniFee")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract fun fromDTO(
        dto: ContributionPeriodDTO,
        @MappingTarget contributionPeriod: ContributionPeriod
    ): ContributionPeriod

    @Mapping(target = "id")
    @Mapping(target = "startDate")
    @Mapping(target = "endDate")
    @Mapping(target = "halfYearFee")
    @Mapping(target = "fullYearFee")
    @Mapping(target = "alumniFee")
    @Mapping(target = "listId")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun toDTO(contributionPeriod: ContributionPeriod): ContributionPeriodDTO
}
