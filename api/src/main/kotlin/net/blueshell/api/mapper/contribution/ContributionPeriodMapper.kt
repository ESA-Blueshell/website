package net.blueshell.api.mapper.contribution

import net.blueshell.api.mapper.base.BaseMapper
import net.blueshell.api.dto.contribution.ContributionPeriodDTO
import net.blueshell.api.model.contribution.ContributionPeriod
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
